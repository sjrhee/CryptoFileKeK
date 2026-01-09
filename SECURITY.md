# Security Policy

## 보안 취약점 보고 (Reporting Security Vulnerabilities)

이 프로젝트의 보안 취약점을 발견하신 경우, 공개 이슈 트래커에 보고하지 마시고 다음 절차를 따라주세요:

1. **비공개 보고**: 저장소 관리자에게 직접 연락
2. **상세 정보 제공**: 취약점의 성격, 재현 방법, 잠재적 영향도 포함
3. **응답 시간**: 보고된 취약점은 48시간 내 검토됩니다

## 보안 감사 보고서

이 프로젝트는 2026년 1월 보안 감사를 완료했습니다. 상세�� 감사 결과는 다음 문서를 참조하세요:

- [보안 감사 전체 보고서](docs/security/SECURITY_AUDIT_2026-01.md)
- [프로젝트 구조 보안 검토](docs/security/PROJECT_STRUCTURE_SECURITY.md)
- [암호화 처리 절차 보안 점검](docs/security/ENCRYPTION_PROCESS_SECURITY.md)

## 주요 보안 이슈 현황

### 🔴 치명적 (Critical) - 즉시 조치 필요
- Spring Security 미적용
- 세션 데이터 메모리 저장 (Thread-safety 부재)
- DEK가 HTTP 응답에 포함
- 임시 파일 안전 삭제 부재

### 🟡 고위험 (High) - 1주일 내 조치
- 경로 탐색 취약점
- 세션 하이재킹 방지 부족
- 파일-DEK 매칭 검증 부재
- 에러 메시지 정보 노출

### 🟠 중간 위험 (Medium) - 1개월 내 조치
- HTTPS 강제 설정 부재
- CORS 설정 부재
- 파일 크기 검증 미흡
- 동시성 제어 부족

## 보안 모범 사례

### 1. 인증 및 권한
- [ ] Spring Security 통합
- [ ] API 엔드포인트 인증 적용
- [ ] RBAC (Role-Based Access Control) 구현

### 2. 데이터 보호
- [x] AES-256-GCM 암호화 사용
- [x] 랜덤 IV 생성
- [ ] 임시 파일 안전 삭제
- [ ] DEK 전송 보안 강화

### 3. 입력 검증
- [ ] 파일명 경로 탐색 방지
- [ ] 파일 크기 제한 검증
- [ ] 파일 타입 화이트리스트

### 4. 보안 헤더
- [ ] HTTPS 강제
- [ ] CSP (Content Security Policy)
- [ ] X-Frame-Options
- [ ] HSTS (HTTP Strict Transport Security)

## 지원되는 버전

| 버전 | 지원 여부 |
| --- | --- |
| 1.0.x | :white_check_mark: |

## 보안 업데이트 이력

### 2026-01-09
- 초기 보안 감사 완료
- 15건의 보안 이슈 식별
- 우선순위별 조치 계획 수립

## 참고 자료

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [NIST Cryptographic Standards](https://csrc.nist.gov/)

---

**Last Updated**: 2026-01-09
Current Date and Time (UTC - YYYY-MM-DD HH:MM:SS formatted): 2026-01-09 12:10:56
Current User's Login: sjrhee
