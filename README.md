# Takt

Wear OS 스마트워치용 **진동 메트로놈**. 폰 없이 워치 단독으로 동작합니다.
(Galaxy Watch 4 이상 / Wear OS 3+ , minSdk 30)

## 기능
**메인 화면**
- BPM 20–300: 베젤/크라운 회전(빠르게 돌리면 가속), −/+ (길게 누르면 ±10), TAP(탭 템포)
- 화면 테두리 링: 다음 박 칸이 차오르다 꽉 차는 순간 진동, 울린 칸은 번쩍였다 흐려짐. **링 칸을 탭하면 그 박을 보통 → 강박 → 무음으로 전환** (3+2+2 같은 그룹핑)
- BPM 아래 템포 이름(Andante, Allegro …)

**설정**
- 마디당 박 1–12, 세분음표(4분/8분/셋잇단/16분: 박 사이에 약한 짧은 진동)
- 첫 박 강세, 진동 세기 5단계(진폭+길이, 기본 최강) — 정지 상태에서 바꾸면 미리보기 진동
- 또렷한 클릭: 기기 맞춤 시스템 클릭 효과(짧고 센 진동)
- 박마다 화면 전체 번쩍임 (강박은 더 세게)
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
설정 > 타이밍에서 고를 수 있습니다.

**박마다 직접 진동 (기본)**
- 전용 고우선순위 스레드가 펄스(박·세분)마다 정확한 시각에 짧은 진동을 겁니다.
- 목표 시각 0.7ms 전까지는 잠들고, 마지막 구간만 바쁜 대기로 맞춥니다 (JVM 측정: 앱 쪽 오차 0.3ms 이하).
- 진동을 "다시 걸" 일이 없어서 설정 변경·화면 꺼짐에도 박이 튀지 않습니다.
- 진동 서비스 응답 시간(측정값)만큼 자동으로 일찍 걸고, 모터 기동 지연은 "진동 앞당기기"(기본 +10ms)로 보정.
- 설정 화면에 진단 표시: 앱 오차(목표 시각 대비 호출 시점), 시스템 응답(vibrate() 호출에 걸린 시간).

**시스템 반복 패턴**
- 반복 진동 파형 하나를 시스템에 넘겨 시스템이 타이밍을 잽니다. 정상 상태에선 정밀하지만,
  다시 걸 때(설정 변경, 화면 꺼짐)마다 시스템 처리 지연만큼 한 번 튈 수 있습니다.
- 주기 = 마디의 정수배, 가능하면 `lcm(BPM, 마디당 박)` 박이라 반복해도 오차 누적 없음.
- 다시 걸 때는 다음 "펄스"부터 이어서 세분 펄스가 빠지지 않습니다.

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
