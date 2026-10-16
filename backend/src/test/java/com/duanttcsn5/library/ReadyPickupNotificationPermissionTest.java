package com.duanttcsn5.library;

import com.duanttcsn5.library.controller.ReadyPickupNotificationController;
import com.duanttcsn5.library.dto.book.ReadyPickupNotificationDtos.TriggerRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.ReadyPickupNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadyPickupNotificationPermissionTest {
    private AnnotationConfigApplicationContext context;
    private ReadyPickupNotificationController controller;
    private ReadyPickupNotificationService service;
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ReadyPickupNotificationService service(){return mock(ReadyPickupNotificationService.class);}
        @Bean ReadyPickupNotificationController controller(ReadyPickupNotificationService service){return new ReadyPickupNotificationController(service);}
    }
    @BeforeEach void setup(){
        context=new AnnotationConfigApplicationContext(Config.class);
        controller=context.getBean(ReadyPickupNotificationController.class);service=context.getBean(ReadyPickupNotificationService.class);
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();context.close();}
    private void login(String role){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "staff","",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","LIBRARIAN","LIBRARY_MANAGER"})
    void authorizedStaffCanReadHistoryAndRunBothModes(String role){
        login(role);controller.history(20);
        UserPrincipal principal=new UserPrincipal(7L,"staff@example.com","Nhân viên",role,0);
        controller.trigger(20,new TriggerRequest(false),principal);controller.trigger(20,new TriggerRequest(true),principal);
        verify(service).history(20);verify(service).trigger(20,false,7L);verify(service).trigger(20,true,7L);
    }
    @Test void readerCannotReadEmailHistoryOrSendTestEmails(){
        login("READER");assertThrows(AuthorizationDeniedException.class,()->controller.history(20));
        assertThrows(AuthorizationDeniedException.class,()->controller.trigger(20,new TriggerRequest(true),null));
        verifyNoInteractions(service);
    }
    @Test void anonymousCannotUseEitherEndpoint(){
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.history(20));
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->controller.trigger(20,null,null));
        verifyNoInteractions(service);
    }
}
