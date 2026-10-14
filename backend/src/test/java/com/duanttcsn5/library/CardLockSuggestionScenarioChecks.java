package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.librarycard.*;
import com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.*;
import com.duanttcsn5.library.entity.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.repository.*;
import com.duanttcsn5.library.service.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Exercises the real workflow with in-memory persistence doubles; no database; also verifies real Spring method-security proxies. */
public final class CardLockSuggestionScenarioChecks {
    static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-14T11:00:00+07:00");
    static int checks;
    static void check(boolean condition,String name) { if(!condition) throw new AssertionError(name); checks++; }
    static BigDecimal money(long value) { return BigDecimal.valueOf(value); }
    static class Store extends CardLockSuggestionRepository {
        Settings setting=new Settings(100000,NOW,null,null);
        final Map<Long,Suggestion> rows=new LinkedHashMap<>();
        final Map<Long,Evidence> evidence=new HashMap<>();
        List<OverdueItem> overdue=List.of(); List<OutstandingFee> fees=List.of(); long next=1;
        Store() { super(null,null); }
        @Override public void lockScan() {}
        @Override public Settings settings() { return setting; }
        @Override public List<Long> cardIds() { return List.of(5L); }
        @Override public Long cardId(Long id) { return rows.containsKey(id)?5L:null; }
        @Override public Optional<Suggestion> find(Long id) { return Optional.ofNullable(rows.get(id)); }
        @Override public Optional<Suggestion> openEpisode(Long id) { return rows.values().stream().filter(Suggestion::episodeOpen).findFirst(); }
        @Override public List<OverdueItem> overdue(Long reader,OffsetDateTime day) { return overdue; }
        @Override public List<OutstandingFee> fees(Long reader) { return fees; }
        @Override public void checkedAt(OffsetDateTime now) { setting=new Settings(setting.debtThresholdVnd(),setting.updatedAt(),setting.updatedBy(),now); }
        @Override public void updateThreshold(long value,Long actor,OffsetDateTime now) { setting=new Settings(value,now,actor,setting.lastCheckedAt()); }
        @Override public Long create(Long card,String number,String name,long days,BigDecimal debt,long threshold,String reason,Evidence e,OffsetDateTime now) {
            long id=next++; rows.put(id,new Suggestion(id,card,number,name,days,debt,threshold,reason,"PENDING",true,now,now,null,null,null,null,null,null,null,"ACTIVE")); evidence.put(id,e);return id;
        }
        @Override public void updatePending(Long id,long days,BigDecimal debt,long threshold,String reason,Evidence e,OffsetDateTime now) {
            var s=rows.get(id); rows.put(id,new Suggestion(id,s.cardId(),s.cardNumber(),s.readerName(),days,debt,threshold,reason,s.status(),s.episodeOpen(),s.detectedAt(),now,null,null,null,null,null,null,null,s.currentCardStatus()));evidence.put(id,e);
        }
        void terminal(Long id,String status,boolean open,Long actor,String name,String reason,String cardStatus,OffsetDateTime end,OffsetDateTime resolved) {
            var s=rows.get(id); rows.put(id,new Suggestion(id,s.cardId(),s.cardNumber(),s.readerName(),s.maxOverdueDays(),s.unpaidFeesVnd(),s.debtThresholdVnd(),s.reasonCode(),status,open,s.detectedAt(),NOW,actor,name,actor==null?null:NOW,reason,cardStatus,end,resolved,cardStatus==null?s.currentCardStatus():cardStatus));
        }
        @Override public void endEpisode(Long id,OffsetDateTime now) {
            var s=rows.get(id);terminal(id,s.status().equals("PENDING")?"RESOLVED":s.status(),false,s.decidedBy(),s.deciderName(),s.decisionReason(),s.cardStatusAfterDecision(),s.lockEndsAt(),now);
        }
        @Override public void closeLockCycle(Long id,OffsetDateTime now) { var s=rows.get(id);terminal(id,s.status(),false,s.decidedBy(),s.deciderName(),s.decisionReason(),s.cardStatusAfterDecision(),s.lockEndsAt(),null); }
        @Override public void cancelLocked(Long id,OffsetDateTime now) { terminal(id,"CANCELLED_LOCKED",true,null,null,null,null,null,null); }
        @Override public void decide(Long id,String status,Long actor,String name,String reason,String cardStatus,OffsetDateTime end,OffsetDateTime now) { terminal(id,status,true,actor,name,reason,cardStatus,end,null); }
        @Override public Evidence evidence(Long id) { return evidence.get(id); }
    }
    static class Fixture {
        Store store=new Store(); LibraryCard card=new LibraryCard(); int locks; long countedDays;
        CardLockSuggestionService service;
        Fixture() {
            User reader=new User();reader.setId(20L);reader.setFullName("Bạn đọc A");reader.setStatus("ACTIVE");
            card.setId(5L);card.setUser(reader);card.setCardNumber("TEST-A");card.setStatus("ACTIVE");
            User manager=new User();manager.setId(12L);manager.setFullName("Quản lý A");manager.setStatus("ACTIVE");Role role=new Role();role.setCode("LIBRARY_MANAGER");manager.setRole(role);
            LibraryCardRepository cards=(LibraryCardRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{LibraryCardRepository.class},(p,m,a)->switch(m.getName()) {
                case "findReaderId" -> Optional.of(20L); case "findByIdForUpdate" -> Optional.of(card);case "lockReaderForTransactions" -> 1;default -> throw new UnsupportedOperationException(m.getName()); });
            UserRepository users=(UserRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{UserRepository.class},(p,m,a)->m.getName().equals("findById")?Optional.of(manager):null);
            var config=new LibraryConfigurationService(null,null,null,null,null) {
                @Override public Map<LocalDate,Long> calculateOverdueOpenDays(Set<LocalDate> dates,LocalDate today) {
                    Map<LocalDate,Long> result=new HashMap<>();for(LocalDate date:dates) result.put(date,countedDays-(date.equals(NOW.toLocalDate().minusDays(2))?10:0));return result;
                }
            };
            var cardService=new LibraryCardService(null,null,null,null,null) {
                @Override public LibraryCardResponse lockCard(Long id,LockLibraryCardRequest request,Long actor,String ip) {
                    locks++;card.applyManualLock(request.reason(),NOW,request.endsAt(),actor);return response();
                }
                @Override public LibraryCardDetailsResponse getCardDetails(Long id) { return new LibraryCardDetailsResponse(response(),"Quản lý A",List.of()); }
            };
            service=new CardLockSuggestionService(store,cards,cardService,config,users,Clock.fixed(NOW.toInstant(),ZoneOffset.ofHours(7)));
        }
        LibraryCardResponse response() {
            return new LibraryCardResponse(5L,"TEST-A",20L,"Bạn đọc A","BD-A",1L,"Sinh viên",NOW.toLocalDate().minusYears(1),NOW.toLocalDate().plusYears(1),card.hasActiveManualLock(NOW)?"LOCKED":"ACTIVE",NOW,LibraryCardLockResponse.fromEntity(card,NOW),money(0));
        }
        void debt(long amount) { store.fees=amount==0?List.of():List.of(new OutstandingFee(8L,"Phí",money(amount),money(0),money(0),money(amount))); }
        void overdue(long days) { countedDays=days;store.overdue=List.of(new OverdueItem(1L,"PM-A",2L,"Sách A","COPY-A",NOW.minusDays(40),0)); }
        Suggestion first() { return store.rows.values().iterator().next(); }
    }
    @org.springframework.context.annotation.Configuration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class MethodSecurityConfig {}
    static void permissionChecks() {
        Fixture f=new Fixture();
        try(var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(MethodSecurityConfig.class);
            context.registerBean(CardLockSuggestionService.class,()->f.service);
            context.refresh();
            var secured=context.getBean(CardLockSuggestionService.class);
            for(String role:List.of("ADMIN","LIBRARIAN","READER")) {
                org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test","",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role))));
                List<Runnable> actions=List.of(()->secured.settings(),()->secured.list(false,0,20),()->secured.detail(1L),
                    ()->secured.updateSettings(new UpdateSettings(money(100000)),12L),
                    ()->secured.accept(1L,new LockLibraryCardRequest("Vi phạm",null),12L,"127.0.0.1"),
                    ()->secured.ignore(1L,new Ignore("Xem xét"),12L));
                for(Runnable action:actions) {
                    try { action.run();throw new AssertionError("Unauthorized role: "+role); }
                    catch(org.springframework.security.access.AccessDeniedException expected) { checks++; }
                }
            }
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            try { secured.settings();throw new AssertionError("Guest authorized"); }
            catch(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException expected) { checks++; }
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("manager","",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_LIBRARY_MANAGER"))));
            check(secured.settings().debtThresholdVnd()==100000,"manager can read through actual security proxy");
            check(secured.updateSettings(new UpdateSettings(money(200000)),12L).debtThresholdVnd()==200000,"manager can update through actual security proxy");
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    public static void runAll() {
        checks=0;
        check(CardLockSuggestionService.reasonCode(30,money(100000),100000)==null,"30 days and debt equals threshold: no violation");
        check("OVERDUE".equals(CardLockSuggestionService.reasonCode(31,money(100000),100000)),"31 days triggers overdue");
        check("DEBT".equals(CardLockSuggestionService.reasonCode(0,money(100001),100000)),"strict debt threshold");
        check("BOTH".equals(CardLockSuggestionService.reasonCode(31,money(150000),100000)),"both reasons");
        Fixture f=new Fixture();f.overdue(30);f.service.refresh();check(f.store.rows.isEmpty(),"refresh: 30 days ignored");
        f.overdue(31);f.service.refresh();check(f.store.rows.size()==1 && f.first().status().equals("PENDING"),"refresh: 31 days pending");
        check(f.locks==0,"detection never locks");f.service.refresh();check(f.store.rows.size()==1,"no duplicate pending");
        f.countedDays=35;f.store.overdue=new ArrayList<>(f.store.overdue);f.store.overdue.add(new OverdueItem(3L,"PM-B",4L,"Sách B","COPY-B",NOW.toLocalDate().minusDays(2).atStartOfDay().atOffset(ZoneOffset.ofHours(7)),0));f.service.refresh();
        check(f.first().maxOverdueDays()==35 && f.store.evidence.get(1L).overdueItems().size()==2,"maximum across multiple loans and full details");
        f.debt(150000);f.service.refresh();check(f.first().reasonCode().equals("BOTH"),"pending evidence updates");
        var ignored=f.service.ignore(1L,new Ignore("Đang xử lý hồ sơ"),12L);
        check(ignored.suggestion().status().equals("IGNORED") && f.locks==0,"ignore never locks");
        check(ignored.suggestion().decidedBy()==12L && ignored.suggestion().deciderName().equals("Quản lý A") && ignored.suggestion().decidedAt()!=null,"decision actor and time");
        f.debt(200000);f.service.refresh();check(f.store.rows.size()==1 && f.first().unpaidFeesVnd().equals(money(150000)),"ignored episode suppressed; history frozen");
        f.store.overdue=List.of();f.debt(0);f.service.refresh();check(!f.first().episodeOpen() && f.first().status().equals("IGNORED"),"recovery ends episode, preserves ignore decision");
        f.debt(150000);f.service.refresh();check(f.store.rows.size()==2,"new violation creates new episode");
        Fixture paid=new Fixture();paid.debt(150000);paid.service.refresh();paid.debt(0);
        var noLock=paid.service.accept(1L,new LockLibraryCardRequest("Khóa do nợ",NOW.plusDays(7)),12L,"127.0.0.1");
        check(!noLock.accepted() && paid.locks==0 && noLock.suggestion().status().equals("RESOLVED"),"payment before approval ends proposal without lock");
        Fixture returned=new Fixture();returned.overdue(35);returned.service.refresh();returned.store.overdue=List.of();
        check(!returned.service.accept(1L,new LockLibraryCardRequest("Quá hạn",null),12L,"127.0.0.1").accepted() && returned.locks==0,"return before approval prevents lock");
        Fixture accepted=new Fixture();accepted.debt(150000);accepted.service.refresh();
        var locked=accepted.service.accept(1L,new LockLibraryCardRequest("Xác nhận vi phạm",NOW.plusDays(7)),12L,"127.0.0.1");
        check(locked.accepted() && accepted.locks==1 && locked.card().status().equals("LOCKED"),"accept uses manual lock");
        check(locked.suggestion().cardStatusAfterDecision().equals("LOCKED") && locked.suggestion().lockEndsAt().equals(NOW.plusDays(7)),"history includes lock status and duration");
        accepted.service.refresh();check(accepted.store.rows.size()==1 && accepted.first().status().equals("ACCEPTED"),"accepted history survives scans");
        try { accepted.service.accept(1L,new LockLibraryCardRequest("Trùng",null),12L,"127.0.0.1");throw new AssertionError("duplicate accepted"); }
        catch(ApiException expected) { checks++; }
        accepted.card.releaseManualLock(NOW);accepted.service.refresh();check(accepted.store.rows.size()==2,"lock released with ongoing violation creates a new review, retaining accepted history");
        Fixture manual=new Fixture();manual.debt(150000);manual.card.applyManualLock("Thủ công",NOW,null,12L);manual.service.refresh();check(manual.store.rows.isEmpty(),"manual lock before scan excluded");
        Fixture racing=new Fixture();racing.debt(150000);racing.service.refresh();racing.card.applyManualLock("Thủ công",NOW,null,12L);
        var obsolete=racing.service.accept(1L,new LockLibraryCardRequest("Đề xuất",null),12L,"127.0.0.1");
        check(!obsolete.accepted() && racing.locks==0 && obsolete.suggestion().status().equals("CANCELLED_LOCKED"),"manual lock before approval prevents duplicate");
        Fixture setting=new Fixture();setting.debt(150000);setting.service.updateSettings(new UpdateSettings(money(200000)),12L);check(setting.store.rows.isEmpty(),"higher threshold prevents proposal");
        setting.service.updateSettings(new UpdateSettings(money(100000)),12L);check(setting.store.rows.size()==1,"lower threshold refreshes immediately");
        setting.service.updateSettings(new UpdateSettings(money(150000)),12L);check(setting.first().status().equals("RESOLVED"),"equal threshold resolves pending");
        for(BigDecimal invalid:List.of(new BigDecimal("-1"),new BigDecimal("0.5"),new BigDecimal("1000000000001"))) {
            try { setting.service.updateSettings(new UpdateSettings(invalid),12L);throw new AssertionError("invalid threshold"); }catch(ApiException expected) { checks++; }
        }
        Fixture partial=new Fixture();partial.store.fees=List.of(new OutstandingFee(1L,"Trễ hạn",money(100000),money(20000),money(30000),money(50000)),new OutstandingFee(2L,"Bồi thường",money(70000),money(0),money(0),money(70000)));partial.service.refresh();
        check(partial.first().unpaidFeesVnd().equals(money(120000)),"sum remaining amounts across categories");
        permissionChecks();
        var mapper=new tools.jackson.databind.ObjectMapper();
        var evidence=new Evidence(List.of(),List.of(new OutstandingFee(1L,"Phí",new BigDecimal("9007199254740993"),money(0),money(0),new BigDecimal("9007199254740993"))));
        String json=mapper.writeValueAsString(evidence);
        check(json.contains("\"9007199254740993\""),"money is serialized as exact string");
        check(mapper.readValue(json,Evidence.class).fees().get(0).outstandingVnd().equals(new BigDecimal("9007199254740993")),"evidence JSON round-trip preserves large amounts");
        System.out.println("PASS: "+checks+" S4-04.3 workflow checks (in-memory persistence doubles)");
    }
    public static void main(String[] args) { runAll(); }
}
