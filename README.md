# 파일 암호화 시스템 (File Encryption System)

이 프로젝트는 HSM(Hardware Security Module)과 연동하여 파일을 안전하게 암호화하고 복호화하는 Spring Boot 기반의 웹 애플리케이션입니다.
암호화 키(DEK)는 HSM의 KEK(Key Encryption Key)로 이중 보호됩니다.

## 주요 기능 (Features)

### 1. 파일 암호화 (Encryption)
- **랜덤 DEK 생성**: 파일마다 고유한 256비트 AES DEK(Data Encryption Key)를 생성합니다.
- **파일 암호화**: 서버의 `DATA` 디렉토리에 있는 파일을 AES-256-GCM 알고리즘으로 암호화합니다.
- **DEK 암호화 (Key Wrapping)**: 생성된 DEK는 HSM의 KEK를 사용하여 안전하게 암호화됩니다.
- **결과물**: `DATA` 디렉토리에 암호화된 파일(`.encrypted`)과 암호화된 DEK 파일(`.dek`)이 생성됩니다.

### 2. 파일 복호화 (Decryption)
- **파일 복원**: 암호화된 파일과 대응하는 DEK 파일을 사용하여 원본 파일을 복원합니다.
- **Key Unwrapping**: HSM을 통해 암호화된 DEK를 복호화하여 사용 가능한 DEK를 추출합니다.
- **검증**: 복호화된 DEK로 파일의 암호화를 해제하고 원본 데이터를 검증합니다.

### 3. 서버 기반 파일 처리 (Server-Side File Processing)
- **DATA 디렉토리**: 모든 파일 작업은 서버의 `DATA` 디렉토리 내에서 이루어집니다.
- **단일 폴더 워크플로우**: 원본 파일, 암호화된 결과물, 복호화된 결과물이 모두 한 곳에서 관리됩니다.

### 4. 모의 HSM (Simulated HSM)
- 실제 HSM 장비가 없는 환경을 위해 소프트웨어 기반의 모의 HSM 서비스를 내장하고 있습니다.
- 애플리케이션 시작 시 KEK를 자동 생성/로드하여 암호화 작업을 시뮬레이션합니다.
- **주의**: 운영 환경에서는 실제 HSM 장비 연동 코드로 교체해야 합니다.

## 기술적 상세 (Technical Details)

### 암호화 오버헤드 (Encryption Overhead)
파일이 암호화되면 **원본 크기보다 정확히 28바이트가 증가**합니다. 이유는 다음과 같습니다:
- **IV (Initialization Vector)**: 12바이트 (AES-GCM의 고유성 보장을 위해 필요)
- **GCM 인증 태그 (Auth Tag)**: 16바이트 (데이터 무결성 및 위변조 방지)
- **공식**: `암호화된 크기 = 원본 크기 + 12 (IV) + 16 (Tag)`
- 이 오버헤드는 파일 크기와 관계없이 항상 일정합니다.

## 기술 스택 (Tech Stack)
- **Backend**: Java 11, Spring Boot 2.7.18
- **Frontend**: HTML5, CSS3, JavaScript (Vanilla), Thymeleaf (English UI)
- **Build Tool**: Maven

## 실행 방법 (How to Run)

프로젝트 루트 디렉토리에서 제공되는 스크립트를 사용하여 간편하게 제어할 수 있습니다.

### 1. 빌드 (Build)
애플리케이션을 빌드합니다. (Maven 필요)
```bash
./build.sh
```

### 2. 시작 (Start)
애플리케이션을 백그라운드 모드로 시작합니다.
```bash
./start.sh
```
- 접속 주소: `http://localhost:8080`
- 로그 파일: `app.log`

### 3. 종료 (Stop)
실행 중인 애플리케이션을 종료합니다.
```bash
./stop.sh
```

### 4. 재시작 (Restart)
애플리케이션을 종료 후 다시 시작합니다.
```bash
./restart.sh
```
