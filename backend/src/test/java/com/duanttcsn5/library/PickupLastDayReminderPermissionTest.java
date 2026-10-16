package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.PickupLastDayReminderController;
import com.duanttcsn5.library.dto.book.PickupLastDayReminderDtos.*;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PickupLastDayReminderPermissionTest {
    private AnnotationConfigApplicationContext context;
    private PickupLastDayReminderController controller;
    private TrackingService service;
    @Configuration @EnableMethodSecurity static class Config {
        @Bean TrackingService service(){return new TrackingService();}
        @Bean PickupLastDayReminderController controller(TrackingService service){return new PickupLastDayReminderController(service);}
    }
    static class TrackingService extends ReadyPickupNotificationService {
        final List<String> calls=new ArrayList<>();
        TrackingService(){super(new PickupLastDayReminderServiceTest.FakeRepository(),new PickupLastDayReminderServiceTest.CaptureEmail(),new PickupLastDayReminderServiceTest.Transactions(),Runnable::run,"Quầy 1",Clock.systemUTC());}
        @Override public Report previewLastDay(LocalDate date){calls.add("preview");return null;}
        @Override public Report runLastDayToday(LocalDate date,Long actor){calls.add("run:"+actor);return null;}
    }
    @BeforeEach void setup(){context=new AnnotationConfigApplicationContext(Config.class);controller=context.getBean(PickupLastDayReminderController.class);service=context.getBean(TrackingService.class);}
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();context.close();}
    private void login(String role){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("staff","",List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    @Test void allThreeStaffRolesCanPreviewAndRunWithTheirIdentity(){
        for(String role:List.of("ADMIN","LIBRARY_MANAGER","LIBRARIAN")){
            login(role);controller.preview(LocalDate.of(2026,10,15));
            controller.run(new RunRequest(LocalDate.of(2026,10,15)),new UserPrincipal(7L,"staff@example.com","Nhân viên",role,0));
        }
        assertEquals(List.of("preview","run:7","preview","run:7","preview","run:7"),service.calls);
    }
    @Test void readersCannotPreviewOrRunAndServiceIsNotCalled(){
        login("READER");assertThrows(AuthorizationDeniedException.class,()->controller.preview(LocalDate.now()));
        assertThrows(AuthorizationDeniedException.class,()->controller.run(new RunRequest(LocalDate.now()),null));assertTrue(service.calls.isEmpty());
    }
    @Test void anonymousCannotAccessEitherOperation(){
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.preview(LocalDate.now()));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.run(new RunRequest(LocalDate.now()),null));assertTrue(service.calls.isEmpty());
    }
}
