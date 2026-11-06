"""차량도면(검은 배경 1448x1086) → 투명 레이어 4종. 사용: make_layers.py <exterior.png> <xray.png> <out_dir>"""
import sys, numpy as np
from PIL import Image, ImageDraw, ImageFilter

ext_p, xray_p, out = sys.argv[1], sys.argv[2], sys.argv[3]
ext = Image.open(ext_p).convert('RGB'); xr = Image.open(xray_p).convert('RGB')
E = np.asarray(ext).astype(np.float32); X = np.asarray(xr).astype(np.float32)
H, W = E.shape[:2]

def blur(a, r):  # a: float 0..1
    return np.asarray(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'L').filter(ImageFilter.GaussianBlur(r))).astype(np.float32) / 255

def silhouette(lum, thr):
    """배경(테두리에서 flood fill로 닿는 어두운 영역)의 여집합 = 차체. 차체 안의 검은 유리·실내는 남는다."""
    fg = (lum > thr).astype(np.uint8) * 255
    pad = Image.new('L', (W + 2, H + 2), 0); pad.paste(Image.fromarray(fg, 'L'), (1, 1))
    ImageDraw.floodfill(pad, (0, 0), 128)
    return (np.asarray(pad)[1:-1, 1:-1] != 128).astype(np.float32)

# 실물 실루엣: 검은 타이어가 배경과 붙어 있으므로 X-ray(타이어가 밝음)와 합집합으로 구한다
lumE = E.max(axis=2); lumX = X.max(axis=2)
mE = silhouette(lumE, 16)
mX = silhouette(lumX, 70)            # X-ray 외곽 발광(halo) 제외한 선 안쪽
body = np.clip(np.maximum(mE, mX * (blur(mE, 6) > 0.02)), 0, 1)
# 작은 잡티 제거 + 1px 침식 후 부드러운 가장자리(안티에일리어싱 ~2px)
bodyI = Image.fromarray((body * 255).astype(np.uint8), 'L').filter(ImageFilter.MedianFilter(5)).filter(ImageFilter.MinFilter(3))
soft = np.asarray(bodyI.filter(ImageFilter.GaussianBlur(1.3))).astype(np.float32) / 255
alphaE = np.clip((soft - 0.12) / 0.76, 0, 1)

def save(rgb, alpha, name, **kw):
    arr = np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 1) * 255]).astype(np.uint8)
    Image.fromarray(arr, 'RGBA').save(f'{out}/{name}.webp', 'WEBP', method=6, exact=False, **kw)
    Image.fromarray(arr, 'RGBA').save(f'{out}/{name}.png')

# 1) 실물: 가장자리 검은 번짐 제거 — 알파가 낮은 픽셀의 색은 안쪽 색으로 채운다(dilate)
rgbE = E.copy()
inner = np.asarray(Image.fromarray(E.astype(np.uint8)).filter(ImageFilter.MaxFilter(5))).astype(np.float32)
edge = (alphaE < 0.98)[..., None]
rgbE = np.where(edge, np.maximum(E, inner * 0.85), E)
save(rgbE, alphaE, 'ioniq5_exterior', quality=93, alpha_quality=100)

# 2) X-ray 야간(발광): 검은 배경 위 가산 이미지 → alpha = 밝기, 색 = 비프리멀티플라이. 바깥 halo는 12px에 걸쳐 부드럽게 사라짐
lum = X.max(axis=2) / 255
a = np.clip(lum * 1.18, 0, 1) ** 0.82
halo = np.clip(blur(body, 9) * 1.6, 0, 1)                    # 차체 + 바깥 ~12px
a_glow = a * halo
rgbX = np.clip(X / np.maximum(lum, 1e-3)[..., None], 0, 255)   # 최대 채널 = 255로 정규화
rgbX = np.where((lum > 0.02)[..., None], rgbX, np.array([40, 255, 170], np.float32))
save(rgbX, a_glow, 'ioniq5_xray_glow', quality=90, alpha_quality=100)

# 3) X-ray 주간(짙은 선): 밝기 → 짙은 청록 잉크. 면(희미한 채움)은 줄이고 선을 강조
ink = np.clip((lum - 0.10) / 0.62, 0, 1) ** 0.9
fill = np.clip(lum * 0.55, 0, 0.22) * alphaE                 # 차체 안 옅은 채움
a_line = np.clip(np.maximum(ink, fill) * np.clip(blur(body, 2) * 1.4, 0, 1), 0, 1)
shade = (1 - ink)[..., None]
rgbL = np.array([10, 92, 68], np.float32) * (1 - 0.35 * shade) + np.array([70, 150, 124], np.float32) * (0.35 * shade)
save(np.broadcast_to(rgbL, E.shape), a_line, 'ioniq5_xray_line', quality=90, alpha_quality=100)

# 4) 바닥 그림자(주간): 실루엣을 넓게 흐리고 아래로 12px
sh = blur(body, 26) * 0.40
sh = np.roll(sh, 12, axis=0); sh[:12] = 0
save(np.zeros_like(E), sh, 'ioniq5_shadow', quality=80, alpha_quality=90)

# 검증용 합성 미리보기
def comp(bg, layers):
    base = Image.new('RGBA', (W, H), bg)
    for n in layers: base = Image.alpha_composite(base, Image.open(f'{out}/{n}.png'))
    return base.convert('RGB')
comp((10, 11, 14, 255), ['ioniq5_exterior']).save(f'{out}/prev_ext_dark.png')
comp((247, 248, 250, 255), ['ioniq5_shadow', 'ioniq5_exterior']).save(f'{out}/prev_ext_light.png')
d = Image.open(f'{out}/ioniq5_exterior.png'); dd = np.asarray(d).astype(np.float32); dd[..., :3] *= 0.42
night = Image.alpha_composite(Image.alpha_composite(Image.new('RGBA', (W, H), (10, 11, 14, 255)), Image.fromarray(dd.astype(np.uint8), 'RGBA')), Image.open(f'{out}/ioniq5_xray_glow.png'))
night.convert('RGB').save(f'{out}/prev_xray_dark.png')
dl = np.asarray(d).astype(np.float32).copy(); dl[..., 3] *= 0.5
day = Image.new('RGBA', (W, H), (247, 248, 250, 255))
for l in [Image.open(f'{out}/ioniq5_shadow.png'), Image.fromarray(dl.astype(np.uint8), 'RGBA')]: day = Image.alpha_composite(day, l)
xl = np.asarray(Image.open(f'{out}/ioniq5_xray_line.png')).astype(np.float32).copy(); xl[..., 3] *= 0.8
Image.alpha_composite(day, Image.fromarray(xl.astype(np.uint8), 'RGBA')).convert('RGB').save(f'{out}/prev_xray_light.png')
# IoU(실물·X-ray 실루엣)
iou = (np.minimum(mE, mX).sum() / np.maximum(mE, mX).sum())
print('IoU ext/xray silhouette = %.3f' % iou, ' body area = %.3f' % body.mean())
