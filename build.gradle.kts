plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// 프로젝트가 iCloud 동기화 폴더(~/Desktop)에 있어 빌드 산출물에 "파일 2.xml" 충돌 사본이 생기며
// 리소스 이름 검증이 실패함 → 빌드 디렉터리를 iCloud 밖(~/.gradle-builds)으로 이동
allprojects {
    layout.buildDirectory.set(file("${System.getProperty("user.home")}/.gradle-builds/ConsumableLife/${project.name}"))
}
