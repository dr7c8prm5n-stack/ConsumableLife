# Cartalog — 내 차 소모품 카탈로그 (Pleos Connect · 아이오닉 5)

> 앱 이름을 "소모품 라이프" → **Cartalog**로 변경 (2026-11-06). 패키지(`com.example.consumablelife`)·프로젝트 폴더·DB 이름은 기존 설치와 데이터 호환을 위해 그대로 둔다.

명세서: `../필요 자료/pleos_consumable_life_spec.html` (본문 v1.0), `../필요 자료/pleos_ioniq5_lifecycle_spec.html` (v2.0 — 투명 레이어·수명주기),
**`~/Desktop/pleos_consumables_3tab_design_spec.html` (v3 — 3탭 구조·토큰·모션. 화면 레이아웃은 이 문서가 기준)**

## v3 변경 요약 (2026-11-06)
- **3탭 구조** `feature/consumables/` — AppHeader(주행거리 · 탭바 · 요약 카운터 · 설정) + 차량(핫스팟 도식 + 플로팅 상세 + 도크) / 대시보드(도식 카드 + 가장 급한 항목 + 2×5 그리드 + 상세 패널) / 타임라인(히어로 + 거리·기간 레인 + 여유 칩) + 하단 CTA 1개. 상세는 Modal Bottom Sheet(깊이 2), 주행 중은 S5 축약(히어로 1장).
- **토큰** `core/designsystem/Theme.kt`(`AppColors`/`LocalAppColors`) — 순흑·무채 면 + 상태 3색 + accent 블루. 치수는 `Kit.kt`의 `Ui`(목업 px × 1.5625 × 창 배율)로 한 곳에서 관리.
- **도식** `core/designsystem/diagram/HotspotDiagram.kt` — `~/Desktop/차량도면` 원본에서 다시 만든 투명 레이어(`tools/make_layers.py`) + 상태색 마커·% 뱃지. 스캔 경계 페더링, 등장·확대 감속 곡선 등 모션을 부드럽게 다듬음.
- **차량 API** `core/vehicle/` — 누적 주행거리 외에 누적 트립 거리·시간, 고전압 배터리 충전량, 출력 제한 경고, 타이어 저압 기준·공기압, 경고등을 품목과 매칭(`VehicleLive`, 프리셋 `sensor`). API별 결과는 설정 › 차량 연동 상태에서 확인. 자세한 결과는 `~/Desktop/작업내용.html`.

## 빌드 / 실행
```bash
./gradlew :app:assembleMockDebug     # Fake 차량 (SDK·실차 없이 UI 개발, 온보딩 첫 화면에 "데모 데이터로 바로 시작" 버튼)
./gradlew :app:assemblePleosDebug    # Pleos Vehicle SDK 2.3.2 연동
./gradlew :app:testMockDebugUnitTest # LifeCalculator 15.1 + 정책 + 프리셋·도식·핫스팟 배치·사이클 + Fake 시나리오 테스트 (58개)
adb install -r -g ~/.gradle-builds/ConsumableLife/app/outputs/apk/mock/debug/app-mock-debug.apk
```
에뮬레이터: `emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load` (cold boot). 앱은 user 10으로 실행되므로 데이터 초기화는 `adb shell pm clear --user 10 com.example.consumablelife.mock`.

## 구조
- `core/domain` LifeCalculator (순수 Kotlin), 상태 판정·정렬, 사용 사이클(`Cycle.kt`), 주기 종류 `CycleKind { REPLACE, INSPECT, USER }`
- `core/vehicle` VehicleDataSource · UxRestrictionMonitor / `src/mock` Fake / `src/pleos` PleosVehicleDataSource
- `core/data` Room(v2) · DataStore · PresetLoader · Repository(프로필 동기화 `syncProfile`)
- `core/designsystem` 토큰·배경 그라데이션·유리 카드, MotionPolicy, LifeRing, StatusChip, **diagram/** (아이오닉 5 도식·라이프 루프·이력 차트)
- `feature/*` 온보딩 · **consumables(3탭 화면)** · detail(상세 본문·다이얼로그) · 교체/점검 기록 · 설정
- `ui/ItemUi.kt` 세 탭이 공유하는 표시 모델(정렬 DB-02, 요약 묶음 DB-03, 차량 실측값 `LiveFact`)

## 아이오닉 5 투명 레이어 도식 · 수명주기 (명세 v2.0, `core/designsystem/diagram/`)
- **단일 원본 JSON** `assets/diagram/ioniq5_lifecycle_hotspots.json` — 좌표(앵커·슬롯·하이라이트)와 품목·주기(`cycle`)를 한 파일에서 읽는다. `DiagramSpec`(좌표)과 `PresetLoader`(품목·주기)가 같은 파일을 파싱하며 코드에 좌표·주기 하드코딩 없음. 본문 5장 프리셋 필드(`icon`·`powertrains`·`sensor`·`zone`·`severe`)는 이 파일의 각 품목에 합쳐 두었다.
- **투명 레이어 5종** `res/drawable-nodpi/ioniq5_{exterior,xray_glow,xray_line,shadow}.webp` (1448×1086 RGBA). `Ioniq5Diagram`은 배경을 칠하지 않는다(DG2-01) — 앱 루트의 배경 그라데이션(`Modifier.appBackdrop`)이 차량 주변에 그대로 비친다. 야간 테마는 발광 X-ray(glow), 주간은 짙은 선(line) + 바닥 그림자(DG2-02).
- **레이어** L1 그림자(주간) → L2 실물(투시 영역은 야간 42% 밝기 / 주간 50%) → L3 X-ray(스캔 영역만) → L4 하이라이트(원 글로우·고전압 배터리 사각형·바퀴 타원, 같은 존·같은 형태는 최악 1개) → L5 리더선·앵커 → L6 반투명 콜아웃 → L7 스캔선(차체 알파로 마스크).
- **보기** 투시(XRAY, 기본) / 실물(EXTERIOR) 76dp 토글 — DataStore `diagramView` 저장, 전환은 300ms 크로스페이드(스캔 재생 안 함, DG2-AN-10).
- **인트로**(투시 첫 진입, 프로세스당 1회, `DiagramTimeline`): 0–400 실물 등장(8dp 위로) → 400–1300 스캔 좌→우(왼쪽 투시) → 1300–1700 하이라이트·앵커 → 1450–2300 리더선·콜아웃·링(슬롯 순 50ms) → 2300–2900 OVERDUE 앵커 펄스 2회. MotionPolicy NONE이면 즉시 최종 상태, 백그라운드 복귀·창 크기·테마 변경 시 재생 없음.
- **모드** OVERVIEW(S1) / FOCUS(S2 — 실물 위 선택 앵커 중심 반경 150px X-ray 렌즈 + 1.6배) / COMPACT(콜아웃 폭 < 200dp → 앵커 + 아래 목록) / GLANCE(주행 제한 — 실물 정적 + 최우선 1개, 탭 무반응).
- **주기 종류**(DG2-I1~I3): INSPECT 품목은 "경과/점검 시기/점검 완료" 어휘, USER 품목은 "기본값 · 수정 가능" 배지(주기를 수정하면 제거), 냉각수는 기록 0건이면 최초 주기(200,000 km/120개월), 감속기 오일은 가혹 조건에서 INSPECT → REPLACE 120,000 km.
- **라이프 루프**(S2 상단, `LifeLoop`): 0–70/70–90/90–100 구간 고리 + 마커 + 100% 초과 바깥 빨간 호(최대 +25%), 중앙 "사용(경과) n% · k회차", 아래 제한 요인 문구. 진입 시 0 → 현재값 800ms, 교체/점검 완료 시 현재 → 100%(400ms) → 고리 확산(200ms) → 0% 리셋·회차 +1(DG2-AN-12). 아래에 회차별 이력 막대(`CycleHistoryChart`, DG-40~42).
- **메모리** 비트맵은 실물·X-ray 1종·그림자 3장만 캐시(`DiagramBitmaps`), 도식 폭이 724px 이하면 inSampleSize=2 (DG2-03). 애니메이션 값은 draw 람다에서만 읽음 (DG2-04).
- 사용자 요청 확장(명세 외): 선택 시 [확대]·[음영 밝게] 토글(음영은 차체 안에서만), 설정의 "도식 5초 주기 스캔"(기본 꺼짐, 투시 보기·주차 중에만).

## (이전 v2 디자인 메모 — 팔레트·형태는 v3 토큰으로 대체됨) 디자인 `core/designsystem/Theme.kt`
- 팔레트: Hyundai Blue `#002C5F`(주간 주색·CTA), Active Blue `#00AAD2`(야간 주색·강조선). 배경은 Pleos 홈 톤의 방사형 그라데이션(야간 `#2C3F66 → #132033 → #090E17`, 주간 흰색 → Light Sand) 위에 반투명 유리 카드(`glassSurface`, 84%).
- 형태: 카드 12dp·버튼 10dp 낮은 곡률, 얇은 외곽선, 숫자·제목은 ExtraBold + 음수 자간. 헤더 `BrandTitle`(강조선 + "현대 아이오닉 5 · 내 차 소모품" 캡션 + 큰 누적 주행거리).
- 아이콘: 품목은 Outlined + 주색 `IconBadge`, 상태는 Filled + 상태색(색만으로 상태를 전달하지 않음). 상태색은 UX-10 대비 4.5:1 검증값, 도식 팔레트(야간/주간)는 반투명 콜아웃 카드 위 대비 4.5:1을 테스트로 검증.

## 명세와 다르게 처리한 부분
- `ioniq5_combo.webp`(저사양·정적 대체)는 명세 HTML에 포함되어 있지 않아 넣지 않음. 애니메이션 OFF(MotionPolicy NONE)에서는 같은 3장으로 최종 상태(실물 어둡게 + X-ray)를 바로 그린다.
- `ioniq5_intro_preview.webp`(애니메이션 WebP)는 명세대로 앱에 넣지 않음(시안용). 인트로는 코드 애니메이션.
- "배경이 투명한"의 범위는 명세 기본값대로 앱 화면 안의 투명 레이어(Q1). 홈 위젯·오버레이 아님.
- 하이라이트 존 묶음: 명세 JSON에 존 필드가 없어 `zone`(ENGINE_BAY·WINDSHIELD·CABIN·UNDERBODY·DRIVETRAIN·WHEELS)을 추가하고, 같은 존이라도 하이라이트 형태(원/사각형/바퀴 타원 크기)가 다르면 따로 표시(브레이크 패드 46×30 vs 타이어 100×48, DG2-C1).
- 회차: 부록 A 공식(실제 기록 수 + 1). 출고 후 손대지 않은 품목은 mock 데모에서 출고 기준 추정 기록(1회차 · 추정)으로 시작.
- 파워트레인 선택(본문 S0)은 유지하되 기본값 EV(아이오닉 5). EV 전용 품목(고전압 배터리·냉각수·감속기 오일)은 ICE/HEV 선택 시 숨김.
- 기존 설치(DB v1, 구 ICE 품목)는 Room 마이그레이션 1→2 + 앱 시작 시 `syncProfile()`로 프리셋 항목을 아이오닉 5 품목으로 교체(기록·사용자 항목·표시 여부 유지).
- 5초 주기 스캔은 "무한 반복 없음"(MUST)과 충돌하므로 기본 꺼짐·설정 옵션. 15.1 #2/#3 기대값(CAUTION)은 6.1 표와 모순 → 표를 따름. 주간 상태색 3종은 대비 보정. SDK 2.3.2에 단위·차량명 API 없음. 타이어 저압은 `현재 < 기준` 판정.

> 빌드 산출물 위치: `~/.gradle-builds/ConsumableLife/` — 프로젝트가 iCloud 동기화 폴더에 있어 `app/build` 안에 "… 2.xml" 충돌 사본이 생기면 리소스 검증이 실패하므로 iCloud 밖으로 옮김 (root `build.gradle.kts`).
