# VIREL Office — 서하 비서실

폰 홈 화면에 추가해서 쓰는 VIREL STUDIO 모바일 비서실입니다.
노션 허브(`🏢 서하 비서실 허브`)와 한 세트로 움직입니다.

- 라이브: https://lalapert.github.io/virel-office/
- 모바일 미리보기(390×844): https://lalapert.github.io/virel-office/preview.html
- 노션 허브: https://app.notion.com/p/VIREL-Office-4256c80331b781c6991ce92ec8b3e15a

## 구성 — AI 멤버 2명

| 멤버 | 역할 | 담당 |
| --- | --- | --- |
| 제미나이 (팀장) | 전체 설계도 · 작업 배분 · 최종 검수와 수정 | 비서실 설계, 로판 숏드라마 설계·검수, 로블록스 기획 검수 |
| 서하 (실무) | 실행과 보고 | 밑줄노트 블로그, X 이현·시현, 노션 허브·회의록, 일상 톡·일정·브리핑 |

## 화면

| 탭 | 내용 |
| --- | --- |
| 오피스 | 2인 오피스맵과 마커 2개(제미나이·서하), 멤버별 담당 업무 |
| 회의실 | 비서 호출(@전체 · @제미나이 · @서하) 대화, 날짜별 기록, 결정 대기 안건 |
| 작업실 | 할 일 체크리스트(이 기기 저장)와 서하 정기 루틴 |
| 현황 | 제미나이·서하 2명 요약과 담당 업무별 상태 |
| 메모 | 메모 저장, 노션 공유용 복사, JSON 백업 |

## 날짜별 회의실 데이터

```
data/dates.json              날짜 목록 (date, label, notion, sample)
data/chat-YYYY-MM-DD.json    그날 대화
```

```json
{
  "date": "2026-11-27",
  "messages": [
    { "name": "대표님", "text": "하나의 앱에서 둘을 호출하고 싶어" },
    { "name": "서하", "text": "회의실에 @멘션 호출을 넣었어." }
  ]
}
```

- 이름은 `대표님` `제미나이` `서하` 를 씁니다.
- 대표님 말은 오른쪽, 비서 말은 왼쪽에 아바타 색과 함께 표시됩니다.
- `dates.json`에 날짜를 추가하고 `chat-날짜.json`을 올리면 햄버거 메뉴(☰)에 그 날짜가 나타납니다.
- 웹앱에서 나눈 대화는 `노션 대화 동기화` → `JSON 내려받기`로 받아 `data/`에 올리면 다른 기기에서도 같은 기록이 보입니다.

## PWA 설치

- `sw.js`(Service Worker)가 등록되어 있어 Android Chrome에서 `홈 화면에 추가` 시 **앱 설치(standalone)**로 열립니다.
- `manifest.webmanifest` — `id`·`start_url`·`scope` 모두 `./`, `display: standalone`, 아이콘 192/512 + maskable 512.
- 화면 이동은 네트워크 우선(항상 최신), `data/*.json`도 네트워크 우선, 이미지는 캐시 우선 후 갱신입니다. 앱 내용을 바꾸면 `sw.js`의 `VERSION`을 올리면 이전 캐시가 지워집니다.

## 안드로이드 앱 (APK) — 노션 실시간 양방향 연동

`android/` 폴더는 이 웹 UI(`index.html`)를 그대로 담은 Kotlin WebView 앱입니다. 빌드할 때 루트의 `index.html` · `assets/` · `data/`를 앱 안으로 복사하므로 화면은 웹판과 같은 파일 하나로 관리합니다.

| 기능 | 동작 |
| --- | --- |
| ☰ 날짜 드로어 | 노션 허브 하위 `YYYY-MM-DD 회의실` 페이지 목록을 직접 읽음 |
| 회의실 말풍선 | 날짜 페이지 블록의 `이름 : 멘트` 줄을 파싱. 회의실 탭이 보일 때 10초마다 다시 읽음(앱이 뒤로 가면 멈춤) |
| 메시지 전송 | `대표님 : 내용` 문단 블록을 대화 마지막 줄 바로 뒤에 추가. 오늘 페이지가 없으면 허브 아래에 자동 생성 |
| AI 키가 있을 때 | 비서 답장도 `서하 : …` / `제미나이 : …`로 노션에 같이 기록 |
| 전송 실패 | 이 기기에 보관했다가 `노션에 다시 보내기`로 재전송 |
| 오피스 마커 | 오늘 노션에서 제미나이·서하가 마지막으로 한 말을 마커 말풍선에 표시 |

- 노션 호출은 앱의 네이티브 HTTP(OkHttp)가 `api.notion.com`으로 직접 보냅니다(CORS 없음). 허용 경로는 `/v1/blocks` · `/v1/pages` · `/v1/users`뿐입니다.
- **토큰(`ntn_…`)은 앱 ⚙︎ 설정 → `토큰 입력`**에서 넣습니다. Android Keystore(AES-256-GCM)로 암호화해 저장하고, 웹 화면(JS)에는 넘기지 않습니다. 저장소에는 절대 커밋하지 않습니다.
- 파싱 규칙: 한 블록의 첫 줄은 어떤 이름이든 말풍선이 되고, 둘째 줄부터는 `대표님` `제미나이` `서하`로 시작할 때만 새 말풍선이 됩니다(`결론: …` 같은 줄은 앞 말풍선에 이어 붙음).
- 웹판(GitHub Pages)은 지금처럼 JSON 동기화 방식으로 동작합니다.

### 빌드

```bash
cd android
echo "sdk.dir=/path/to/android-sdk" > local.properties   # Android Studio면 자동
./gradlew assembleRelease            # → app/build/outputs/apk/release/app-release.apk
./gradlew testReleaseUnitTest -Dnotion.live=1   # 실제 노션 읽기 테스트 포함(NOTION_TOKEN 환경변수)
```

- `main`에 푸시하면 GitHub Actions(`Android APK`)가 APK를 만들어 실행 결과의 Artifacts(`VIREL-Office-apk`)에 올립니다.
- 서명 키 `android/keystore/virel-shared.keystore`는 로컬 빌드와 Actions 빌드를 같은 서명으로 맞춰 덮어 설치가 되게 하는 **개인 설치용 공용 키**입니다. 공개 저장소에 있으므로 Play 스토어 배포용으로 쓰지 마세요.

## 응답 모드

- **실시간 응답**: 회의실 ⚙︎ 설정에서 Gemini 또는 OpenAI 키를 넣으면 비서가 바로 답합니다. 키는 그 브라우저의 `localStorage`에만 저장되고 이 저장소에는 들어가지 않습니다.
- **노션 전달 모드**: 키가 없으면 지시를 기록만 하고, `노션에 붙일 지시 복사`로 노션 날짜 페이지에 넘깁니다.

키는 절대 코드나 저장소에 커밋하지 마세요.

## 공개 페이지 주의

이 저장소와 페이지는 공개입니다. 연락처·주소·건강·계정·결제 정보는 넣지 않습니다.
