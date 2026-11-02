# Takt

Wear OS 스마트워치용 **진동 메트로놈**. 폰 없이 워치 단독으로 동작합니다.
(Galaxy Watch 4 이상 / Wear OS 3+ , minSdk 30)

## 기능
**메인 화면**
- BPM 20–300: 베젤/크라운 회전(빠르게 돌리면 가속), −/+ (길게 누르면 ±10), TAP(탭 템포)
- 화면 테두리 링이 박마다 켜짐. **링 칸을 탭하면 그 박을 보통 → 강박 → 무음으로 전환** (3+2+2 같은 그룹핑)
- BPM 아래 템포 이름(Andante, Allegro …)

**설정**
- 마디당 박 1–12, 세분음표(4분/8분/셋잇단/16분: 박 사이에 약한 짧은 진동)
- 첫 박 강세, 진동 세기 5단계 (정지 상태에서 바꾸면 미리보기 진동)
- 프리셋 최대 8개 (탭: 불러오기, 길게: 삭제)
- 스피드 트레이너: N마디마다 +X BPM, 목표 BPM 까지. 정확히 마디 첫 박에서 바뀌고, 정지하면 시작 BPM 으로 복원
- 재생 중 화면 켜두기 (선택)

**워치 통합**
- 화면이 꺼져도 계속 동작 (포그라운드 서비스)
- 워치페이스 Ongoing 아이콘: "120 BPM · 03:21" (경과 시간)
- 타일: BPM 표시, −/+ 는 앱을 열지 않고 바로 조절, 재생/정지
- 컴플리케이션: 워치페이스에 현재 BPM (재생 중 ▶)
- Ambient 모드: 재생 중 손목을 내려도 저전력 흑백 화면에 BPM 표시
- 설정 자동 저장

## 타이밍 방식
박마다 앱이 `vibrate()` 를 부르면 스레드 스케줄링 때문에 수 ms 씩 흔들립니다.
Takt 는 **반복 진동 파형 하나**를 시스템 진동 서비스에 넘겨 시스템이 타이밍을 재게 합니다.

- 한 주기 = 마디의 정수배. 가능하면 `lcm(BPM, 마디당 박)` 박으로 잡아 주기 길이가 정확히 정수 ms → 반복해도 오차 누적 없음
- 주기가 너무 길어지면(바인더 전송 한도) 2048 펄스 안에서 ms 오차가 가장 작은 마디 배수를 고르고, 남는 오차(주기당 ≤0.5ms)는 재정렬이 흡수
- 각 펄스 시작은 이상값 대비 ±0.5ms 이내
- 베젤을 빠르게 돌릴 때는 변경을 120ms 모아서 한 번만 다시 걸어 펄스가 잘리지 않게 함
- 템포/박자 변경은 다음 박부터 끊김 없이 적용
- 화면 꺼짐 시 시스템이 진동을 취소하므로 즉시 재설정, 그리고 30초마다 절대 시간축에 다시 정렬

핵심 로직(`BeatGrid`, `HapticPattern`, `TapTempo`, 트레이너, 프리셋, 베젤 가속)은 순수 Kotlin 단위 테스트,
컨트롤러(트레이너가 마디 첫 박에 정확히 바뀌는지, 베젤 디바운스)는 Robolectric 으로 시계를 돌려 검증합니다.

## 테스트 / 스크린샷
```bash
./gradlew testDebugUnitTest          # 단위 + 통합 테스트
./gradlew recordRoborazziDebug       # 메인/설정/타일 스크린샷 → app/build/outputs/roborazzi/
```

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
  ui/       MainScreen, SettingsScreen, BeatRing, RotaryAccumulator
  tile/     MetronomeTileService
  complication/  BpmComplicationService
  Metronome.kt  앱 전역 컨트롤러(상태, 엔진, 재정렬, 트레이너)
```

## 다음 단계 후보
- 소리 모드 (스피커/블루투스)
- 프리셋에 박별 강세 포함
