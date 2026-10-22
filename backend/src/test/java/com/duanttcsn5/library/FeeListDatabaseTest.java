package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.loan.*;
import com.duanttcsn5.library.exception.ApiException;
import com.duanttcsn5.library.service.FeePaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.bootstrap-admin.password=","app.scheduling.enabled=false",
        "app.permission-review.launch-enabled=false","app.pickup-email.automatic-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"})
@EnabledIfEnvironmentVariable(named="S4_09_2_DB_TEST",matches="true")
@ContextConfiguration(initializers=FullPermissionDatabaseTest.DatabaseGuard.class)
@Import(FullPermissionDatabaseTest.SafeTransport.class)
class FeeListDatabaseTest {
    @Autowired FeePaymentService service;
    @Autowired JdbcTemplate jdbc;
    Long user(String role,String name) {
        return jdbc.queryForObject("INSERT INTO users(role_id,full_name,email,phone,status) SELECT id,?,?,'0912345678','ACTIVE' FROM roles WHERE code=? RETURNING id",
                Long.class,name,UUID.randomUUID()+"@example.invalid",role);
    }
    void fee(Long reader) { jdbc.update("INSERT INTO reader_fees(reader_user_id,amount_vnd,description) VALUES (?,10000,'List test')",reader); }
    @Test void pagesHaveTenRowsStableOrderAndDoNotCreateDebtForReadersWithoutFees() {
        String name="FeeList-"+UUID.randomUUID();Long reader=user("READER",name), staff=user("LIBRARIAN","Staff");
        user("READER",name+"-no-fee");
        for(int i=0;i<23;i++)fee(reader);
        var first=service.list(name.toLowerCase(),"","",null,null,0,staff);
        var second=service.list(name,"","",null,null,1,staff);
        var last=service.list(name,"","",null,null,2,staff);
        assertEquals(23,first.totalItems());assertEquals(10,first.size());
        assertEquals(10,first.items().size());assertEquals(10,second.items().size());assertEquals(3,last.items().size());
        Set<Long> ids=new HashSet<>();for(var p:List.of(first,second,last))for(var row:p.items())assertTrue(ids.add(row.id()));
        assertEquals(first,service.list(name,"","",null,null,0,staff));
        assertTrue(service.list(name,"","",null,null,3,staff).items().isEmpty());
        assertEquals(23,service.list(name,"","",null,null,3,staff).totalItems());
        assertTrue(service.list(name,"","COMPENSATION",null,null,0,staff).items().isEmpty());
        assertTrue(service.list(name,"","",LocalDate.of(2000,1,1),LocalDate.of(2000,1,2),0,staff).items().isEmpty());
    }
    @Test void partialAndFullCollectionRemainVisibleWithReceiptAndCorrectFilters() {
        String name="FeeList-"+UUID.randomUUID();Long reader=user("READER",name), staff=user("ADMIN","Admin");fee(reader);
        assertEquals(1,service.list(name,"UNPAID","RECORDED",null,null,0,staff).totalItems());
        var detail=service.detail(reader,staff);
        var receipt=service.collectPartial(reader,new CollectPartialFeesRequest(UUID.randomUUID(),10000L,detail.debtToken(),4000L),staff);
        var partial=service.list(name,"PARTIAL","",null,null,0,staff).items().get(0);
        assertEquals(4000,partial.paidAmountVnd());assertEquals(6000,partial.outstandingAmountVnd());assertEquals(receipt.id(),partial.latestPaymentId());
        var remainder=service.detail(reader,staff);
        service.collect(reader,new CollectFullFeesRequest(UUID.randomUUID(),6000L,remainder.debtToken()),staff);
        var paid=service.list(name,"PAID","",null,null,0,staff).items().get(0);
        assertEquals(10000,paid.paidAmountVnd());assertEquals(0,paid.outstandingAmountVnd());assertNotNull(paid.latestPaymentId());
        assertEquals(0,service.list(name,"PARTIAL","",null,null,0,staff).totalItems());
    }
    @Test void invalidFiltersAndUnauthorizedActorsAreRejected() {
        Long staff=user("LIBRARIAN","Staff");
        for(String role:List.of("READER","LIBRARY_MANAGER")) {
            Long actor=user(role,"Denied");
            assertEquals(403,assertThrows(ApiException.class,()->service.list("","","",null,null,0,actor)).getStatus().value());
        }
        assertThrows(ApiException.class,()->service.list("","OTHER","",null,null,0,staff));
        assertThrows(ApiException.class,()->service.list("","","",null,null,-1,staff));
        assertThrows(ApiException.class,()->service.list("","","",LocalDate.of(2026,2,2),LocalDate.of(2026,2,1),0,staff));
        assertTrue(service.list("%_' OR 1=1 --","","",null,null,0,staff).items().isEmpty());
    }
}
