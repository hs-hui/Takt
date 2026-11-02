# Takt

Wear OS 스마트워치용 **진동 메트로놈**. 폰 없이 워치 단독으로 동작합니다.
(Galaxy Watch 4 이상 / Wear OS 3+ , minSdk 30)

## 기능 (v0.1)
- BPM 20–300: 베젤/크라운 회전, −/+ 버튼, TAP(탭 템포)
- 마디당 박 1–12, 첫 박 강세(더 세고 길게)
- 진동 세기 5단계
- 화면 링으로 현재 박 표시
- 화면이 꺼져도 계속 동작 (포그라운드 서비스 + 워치페이스 Ongoing 아이콘, 알림에서 정지)
- 설정 자동 저장

## 타이밍 방식
박마다 앱이 `vibrate()` 를 부르면 스레드 스케줄링 때문에 수 ms 씩 흔들립니다.
Takt 는 **반복 진동 파형 하나**를 시스템 진동 서비스에 넘겨 시스템이 타이밍을 재게 합니다.

- 한 주기 = `lcm(BPM, 마디당 박)` 박 → 주기 길이가 정확히 정수 ms 라서 반복해도 오차 누적 없음
- 각 박 시작은 이상값 대비 ±0.5ms 이내
- 템포/박자 변경은 다음 박부터 끊김 없이 적용
- 화면 꺼짐 시 시스템이 진동을 취소하므로 즉시 재설정, 그리고 30초마다 절대 시간축에 다시 정렬

핵심 로직(`BeatGrid`, `HapticPattern`, `TapTempo`)은 안드로이드 의존성이 없는 순수 Kotlin 이고 단위 테스트가 있습니다.

## 빌드 & 설치
```bash
./gradlew testDebugUnitTest assembleRelease
# 워치: 설정 > 개발자 옵션 > ADB 디버깅 + 무선 디버깅 켜기
adb pair <워치IP>:<페어링포트>      # 워치에 표시된 코드 입력
adb connect <워치IP>:<포트>
adb install -r app/build/outputs/apk/release/app-release.apk
```
GitHub Actions 가 푸시마다 APK 를 빌드해 아티팩트(`takt-apk`)로 올립니다.
릴리스 빌드는 개인 설치용으로 디버그 키로 서명됩니다.

## 구조
```
app/src/main/java/kr/hui/takt/
  engine/   BeatGrid(박자 격자), HapticPattern(파형 생성), HapticEngine(Vibrator), TapTempo
  service/  MetronomeService (포그라운드 서비스, Ongoing Activity, 화면 꺼짐 처리)
  data/     SettingsRepository (DataStore)
  ui/       MainScreen, SettingsScreen, BeatRing
  Metronome.kt  앱 전역 컨트롤러(상태 + 엔진)
```

## 다음 단계 (예정)
- 세분음표(8분/셋잇단/16분)
- 프리셋
- Tile, Complication
- 스피드 트레이너
- 소리 모드
