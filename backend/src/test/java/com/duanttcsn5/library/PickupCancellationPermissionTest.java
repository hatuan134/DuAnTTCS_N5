package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.ReadyPickupNotificationController;
import com.duanttcsn5.library.dto.book.AutoCancellationRunResponse;
import com.duanttcsn5.library.dto.book.PickupCancellationDtos.RunRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PickupCancellationPermissionTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ReadyPickupNotificationService emails() { return mock(ReadyPickupNotificationService.class); }
        @Bean ReservationAutoCancellationService cancellations() { return mock(ReservationAutoCancellationService.class); }
        @Bean ReadyPickupNotificationController controller(ReadyPickupNotificationService emails) {
            return new ReadyPickupNotificationController(emails);
        }
    }
    private AnnotationConfigApplicationContext context;
    private ReadyPickupNotificationController controller;
    @BeforeEach void setup() { context=new AnnotationConfigApplicationContext(Config.class);controller=context.getBean(ReadyPickupNotificationController.class); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext();context.close(); }
    private void login(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff","",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","LIBRARY_MANAGER"})
    void managerCanRunSelectedCancellation(String role) {
        login(role);
        var cancellations=context.getBean(ReservationAutoCancellationService.class);
        var result=mock(AutoCancellationRunResponse.class);when(result.getTotalCancelled()).thenReturn(1);
        when(cancellations.executeForReservation(eq(20L),isNull(),anyString())).thenReturn(result);
        controller.runCancellation(20,new RunRequest(null),new UserPrincipal(7L,"staff@example.com","Nhân viên",role,0));
        verify(cancellations).executeForReservation(20,null,"staff@example.com");
    }
    @Test void librarianCanPreviewAndReadAllEmailTypesButCannotCancel() {
        login("LIBRARIAN");controller.previewCancellation(20,null);controller.history(20);
        assertThrows(AuthorizationDeniedException.class,()->controller.runCancellation(20,null,null));
        verifyNoInteractions(context.getBean(ReservationAutoCancellationService.class));
    }
    @Test void readerCannotPreviewStaffEmailsOrCancelAnotherReadersOrder() {
        login("READER");assertThrows(AuthorizationDeniedException.class,()->controller.previewCancellation(20,null));
        assertThrows(AuthorizationDeniedException.class,()->controller.runCancellation(20,null,null));
        verifyNoInteractions(context.getBean(ReadyPickupNotificationService.class),context.getBean(ReservationAutoCancellationService.class));
    }
    @Test void anonymousCannotPreviewOrRun() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.previewCancellation(20,null));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.runCancellation(20,null,null));
    }
}
