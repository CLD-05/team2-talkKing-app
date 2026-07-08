package com.team2.talkking.global.test;

import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 🧪 Testing Controller
 * 
 * 시스템 안정성 테스트용 엔드포인트
 * Pod을 실제로 죽여서 Prometheus Alert을 발동시킵니다.
 * 
 * ⚠️ WARNING: Dev/Test 환경에서만 사용!
 * ⚠️ Production에서는 이 클래스를 제거하세요!
 * 
 * 테스트할 수 있는 항목:
 * 1. Pod 강제 종료 (JVM shutdown)
 * 2. Pod 무한 루프 (Liveness Probe 실패)
 * 3. OOMKilled (메모리 부족)
 * 4. CPU 스파이크 (고부하)
 * 5. 정상 응답
 * 6. 시스템 정보 조회
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    // ============================================================
    // ✅ 1️⃣ 정상 응답 테스트 (Baseline)
    // ============================================================
    /**
     * 정상적인 응답을 반환합니다.
     * 
     * 테스트 목적: 시스템이 정상적으로 작동하는지 확인
     * 
     * @return 정상 응답
     */
    @GetMapping("/health-check")
    public Map<String, Object> healthCheck() {
        logger.info("🟢 Health check requested");
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("timestamp", System.currentTimeMillis());
        response.put("pod_name", System.getenv("HOSTNAME"));
        response.put("message", "System is healthy");
        
        return response;
    }

    // ============================================================
    // 🔴 2️⃣ Pod 강제 종료 (System.exit - CrashLoopBackOff 유발)
    // ============================================================
    /**
     * JVM을 강제로 종료하여 Pod을 죽입니다.
     * 
     * 테스트 목적:
     * - Pod이 CrashLoopBackOff 상태로 진입하는지 확인
     * - Prometheus Alert이 발동하는지 확인
     * - AlertManager가 알림을 발송하는지 확인
     * - ArgoCD가 자동으로 재배포하는지 확인
     * 
     * 예상 결과:
     * 1. JVM이 종료됨 (exit code: 1)
     * 2. Pod이 Exited 상태
     * 3. Kubernetes가 Pod 자동 재시작
     * 4. 재시작 반복 → CrashLoopBackOff 상태 진입
     * 5. TalkkingPodCrashLooping Alert 발동
     * 6. Alertmanager가 알림 발송
     * 7. ArgoCD가 자동 재배포 시도
     * 
     * 🔍 확인 방법:
     * kubectl get pods -n talkking-dev -w
     * kubectl describe pod <pod-name> -n talkking-dev
     * 
     * @throws RuntimeException 절대 리턴되지 않음 (JVM이 종료됨)
     */
    @PostMapping("/simulate-crash")
    public void simulateCrash() {
        logger.error("🔴 [TEST] Pod crash test initiated - Forcing JVM shutdown!");
        logger.error("🔴 [TEST] Exit code: 1");
        System.exit(1);  // ← Pod을 실제로 죽임!
    }

    // ============================================================
    // ⏱️ 3️⃣ Pod 무한 루프 (Liveness Probe 실패)
    // ============================================================
    /**
     * 무한 루프를 실행하여 Pod이 응답하지 않게 합니다.
     * 
     * 테스트 목적:
     * - Kubernetes Liveness Probe가 실패를 감지하는지 확인
     * - Liveness Probe 실패 시 Pod이 재시작되는지 확인
     * - 반복되는 재시작으로 CrashLoopBackOff가 진입하는지 확인
     * 
     * 예상 결과:
     * 1. 무한 루프 시작 → Pod이 응답하지 않음
     * 2. Kubernetes Liveness Probe 실패 (타임아웃)
     * 3. Probe 재시도 후에도 실패
     * 4. Kubernetes가 Pod을 강제 종료 (killGracePeriod 후)
     * 5. Pod이 자동 재시작
     * 6. 같은 상황 반복 → CrashLoopBackOff
     * 7. TalkkingPodCrashLooping 또는 TalkkingPodHighRestartRate Alert 발동
     * 
     * 🔍 확인 방법:
     * kubectl logs <pod-name> -n talkking-dev --tail=20
     * 
     * @throws InterruptedException 쓰레드 대기 중 인터럽트 발생 시 (실제로 발생하지 않음)
     */
    @PostMapping("/simulate-hang")
    public void simulateHang() throws InterruptedException {
        logger.error("⏱️ [TEST] Pod hang test initiated - Entering infinite loop!");
        logger.error("⏱️ [TEST] Kubernetes Liveness Probe will fail → Pod will be restarted");
        
        // 무한 루프 - Pod이 모든 요청에 응답하지 않게 됨
        while (true) {
            try {
                Thread.sleep(1000);  // 1초마다 로그 출력
                logger.warn("⏱️ [TEST] Still in infinite loop...");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("⏱️ [TEST] Interrupted (should not happen)");
                break;
            }
        }
    }

    // ============================================================
    // 💾 4️⃣ OOMKilled 시뮬레이션 (메모리 부족)
    // ============================================================
    /**
     * 메모리를 끝없이 할당하여 OOMKilled 상태를 유발합니다.
     * 
     * 테스트 목적:
     * - Pod이 OOMKilled 상태에서 자동 재시작되는지 확인
     * - 메모리 제한 설정이 제대로 작동하는지 확인
     * - HPA가 OOMKilled Pod을 감지하는지 확인
     * - Prometheus가 메모리 사용률을 기록하는지 확인
     * 
     * 예상 결과:
     * 1. 메모리 할당 반복 (1MB씩)
     * 2. 메모리 사용량이 limit에 도달
     * 3. JVM이 OutOfMemoryError 발생 후 종료
     * 4. Pod이 OOMKilled 상태로 표시
     * 5. Kubernetes가 Pod 자동 재시작
     * 6. 반복되면 CrashLoopBackOff 진입
     * 7. TalkkingPodHighRestartRate 또는 TalkkingPodNotReady Alert 발동
     * 
     * 🔍 확인 방법:
     * kubectl describe pod <pod-name> -n talkking-dev | grep -A 5 "State"
     * 
     * Deployment 메모리 제한 예시:
     * resources:
     *   limits:
     *     memory: "512Mi"
     *   requests:
     *     memory: "256Mi"
     * 
     * ⚠️ 주의: 이 엔드포인트를 호출하면 Pod이 즉시 죽습니다!
     */
    @PostMapping("/simulate-oom")
    public void simulateOOM() {
        logger.error("💾 [TEST] OOMKilled test initiated - Allocating unlimited memory!");
        logger.error("💾 [TEST] This will trigger OutOfMemoryError → Pod OOMKilled");
        
        List<byte[]> memory = new ArrayList<>();
        
        // 무한 루프로 메모리 할당
        int count = 0;
        while (true) {
            try {
                memory.add(new byte[1024 * 1024]); // 1MB씩 할당
                count++;
                
                if (count % 100 == 0) {
                    Runtime runtime = Runtime.getRuntime();
                    long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
                    long maxMemory = runtime.maxMemory() / 1024 / 1024;
                    logger.warn("💾 [TEST] Allocated {} MB / Max {} MB", usedMemory, maxMemory);
                }
                
                Thread.sleep(50); // 50ms 딜레이
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("💾 [TEST] Interrupted (should not happen)");
                break;
            }
        }
        // → java.lang.OutOfMemoryError 발생
        // → Pod OOMKilled
    }

    // ============================================================
    // 🔥 5️⃣ CPU 스파이크 (고부하)
    // ============================================================
    /**
     * CPU를 집약적으로 사용하여 CPU 스파이크를 유발합니다.
     * 
     * 테스트 목적:
     * - CPU 모니터링이 제대로 작동하는지 확인
     * - HPA가 CPU 기반 스케일링을 수행하는지 확인
     * - Prometheus가 높은 CPU 사용률을 기록하는지 확인
     * - 시스템이 고부하 상황에서 안정적인지 확인
     * 
     * 예상 결과:
     * 1. CPU 사용률이 100% 가까이 상승
     * 2. Prometheus가 높은 CPU 사용률을 감지
     * 3. HPA가 설정된 CPU threshold를 초과
     * 4. HPA가 새로운 Pod 생성 (스케일 아웃)
     * 5. 계산 완료 후 CPU 사용률 정상화
     * 6. HPA가 시간 경과 후 Pod 수 감소 (스케일 인)
     * 
     * 🔍 확인 방법:
     * kubectl top pods -n talkking-dev (또는 kubectl top nodes)
     * kubectl get hpa -n talkking-dev -w
     * 
     * HPA 설정 예시:
     * spec:
     *   targetCPUUtilizationPercentage: 70
     *   minReplicas: 2
     *   maxReplicas: 10
     * 
     * @return 계산 결과 및 실행 시간
     */
    @PostMapping("/simulate-cpu-spike")
    public Map<String, Object> simulateCPUSpike() {
        logger.warn("🔥 [TEST] CPU spike test initiated - Heavy computation starting!");
        logger.warn("🔥 [TEST] This will consume 100% CPU for ~30 seconds");
        
        long startTime = System.currentTimeMillis();
        long sum = 0;
        
        // 10억 번의 복잡한 계산 (약 30초 소요)
        for (long i = 0; i < 10_000_000_000L; i++) {
            sum += Math.sqrt(i) * Math.sin(i) * Math.cos(i);
            
            // 진행 로깅 (10초마다)
            if (i % 1_000_000_000L == 0 && i > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                logger.warn("🔥 [TEST] CPU Spike Progress: {} billion iterations, {} seconds elapsed", 
                    i / 1_000_000_000L, elapsed / 1000);
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "cpu_spike_completed");
        response.put("duration_ms", duration);
        response.put("duration_seconds", duration / 1000.0);
        response.put("iterations", "10,000,000,000");
        response.put("result", sum);
        response.put("message", "CPU test completed - Pod should have scaled out");
        
        logger.info("🔥 [TEST] CPU Spike completed in {} ms", duration);
        
        return response;
    }

    // ============================================================
    // 📊 6️⃣ 시스템 정보 조회
    // ============================================================
    /**
     * 현재 시스템의 메트릭 정보를 반환합니다.
     * 
     * 테스트 목적:
     * - Pod의 메모리, CPU 제한 설정 확인
     * - 현재 메모리 사용량 확인
     * - Pod이 정상적으로 작동 중인지 확인
     * 
     * 🔍 확인 방법:
     * curl http://pod-ip:8080/api/test/system-info | jq
     * 
     * @return 시스템 메트릭
     */
    @GetMapping("/system-info")
    public Map<String, Object> systemInfo() {
        Runtime runtime = Runtime.getRuntime();
        
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        
        Map<String, Object> info = new HashMap<>();
        info.put("pod_name", System.getenv("HOSTNAME"));
        info.put("jvm_version", System.getProperty("java.version"));
        info.put("memory", new HashMap<String, Object>() {
            {
                put("total_mb", totalMemory);
                put("free_mb", freeMemory);
                put("used_mb", usedMemory);
                put("max_mb", maxMemory);
                put("usage_percent", (double) usedMemory / maxMemory * 100);
            }
        });
        info.put("cpu", new HashMap<String, Object>() {
            {
                put("available_processors", runtime.availableProcessors());
            }
        });
        info.put("timestamp", System.currentTimeMillis());
        info.put("status", "System info retrieved successfully");
        
        return info;
    }

    // ============================================================
    // 📝 7️⃣ 테스트 가이드
    // ============================================================
    /**
     * 모든 테스트 엔드포인트의 가이드를 반환합니다.
     * 
     * @return 테스트 가이드
     */
    @GetMapping("/guide")
    public Map<String, Object> testGuide() {
        Map<String, Object> guide = new HashMap<>();
        
        Map<String, Object> endpoints = new HashMap<>();
        
        endpoints.put("health_check", new HashMap<String, Object>() {
            {
                put("method", "GET");
                put("url", "/api/test/health-check");
                put("description", "정상 응답 테스트");
                put("effect", "Pod이 정상 작동 중인지 확인");
            }
        });
        
        endpoints.put("simulate_crash", new HashMap<String, Object>() {
            {
                put("method", "POST");
                put("url", "/api/test/simulate-crash");
                put("description", "Pod 강제 종료 테스트");
                put("effect", "CrashLoopBackOff 상태 진입 → Alert 발동 → 자동 재배포");
                put("alert", "TalkkingPodCrashLooping");
            }
        });
        
        endpoints.put("simulate_hang", new HashMap<String, Object>() {
            {
                put("method", "POST");
                put("url", "/api/test/simulate-hang");
                put("description", "Pod 무한 루프 테스트");
                put("effect", "Liveness Probe 실패 → Pod 재시작 반복 → CrashLoopBackOff");
                put("alert", "TalkkingPodHighRestartRate / TalkkingPodCrashLooping");
            }
        });
        
        endpoints.put("simulate_oom", new HashMap<String, Object>() {
            {
                put("method", "POST");
                put("url", "/api/test/simulate-oom");
                put("description", "OOMKilled 테스트");
                put("effect", "메모리 부족 → OutOfMemoryError → Pod OOMKilled → 자동 재시작");
                put("alert", "TalkkingPodHighRestartRate / TalkkingPodNotReady");
            }
        });
        
        endpoints.put("simulate_cpu_spike", new HashMap<String, Object>() {
            {
                put("method", "POST");
                put("url", "/api/test/simulate-cpu-spike");
                put("description", "CPU 스파이크 테스트");
                put("effect", "CPU 100% 사용 → HPA 스케일 아웃 → 부하 분산");
                put("duration", "약 30초");
            }
        });
        
        endpoints.put("system_info", new HashMap<String, Object>() {
            {
                put("method", "GET");
                put("url", "/api/test/system-info");
                put("description", "시스템 정보 조회");
                put("effect", "메모리, CPU 사용량 확인");
            }
        });
        
        guide.put("endpoints", endpoints);
        
        Map<String, Object> warnings = new HashMap<>();
        warnings.put("warning_1", "⚠️ 이 엔드포인트들은 Dev/Test 환경에서만 사용하세요!");
        warnings.put("warning_2", "❌ Production 환경에서는 이 클래스를 반드시 제거하세요!");
        warnings.put("warning_3", "🔴 simulate-crash, simulate-hang, simulate-oom은 Pod을 죽입니다!");
        warnings.put("warning_4", "📊 Alert 발동 확인: Prometheus > Alerts 탭");
        
        guide.put("warnings", warnings);
        
        Map<String, Object> testing_flow = new HashMap<>();
        testing_flow.put("step_1", "kubectl get pods -n talkking-dev -w  (Pod 상태 감시)");
        testing_flow.put("step_2", "Prometheus UI 열기 (Alerts 탭)");
        testing_flow.put("step_3", "curl -X POST http://pod-ip:8080/api/test/simulate-crash");
        testing_flow.put("step_4", "Pod 상태 변화 확인 (CrashLoopBackOff)");
        testing_flow.put("step_5", "Prometheus Alert 발동 확인");
        testing_flow.put("step_6", "AlertManager 알림 확인");
        testing_flow.put("step_7", "ArgoCD 자동 재배포 확인");
        
        guide.put("testing_flow", testing_flow);
        
        return guide;
    }
}
