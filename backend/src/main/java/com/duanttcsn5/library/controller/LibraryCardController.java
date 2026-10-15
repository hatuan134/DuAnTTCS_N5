package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.librarycard.ApproveLibraryCardRequest;
import com.duanttcsn5.library.dto.librarycard.LibraryCardResponse;
import com.duanttcsn5.library.dto.librarycard.MyLibraryCardResponse;
import com.duanttcsn5.library.dto.librarycard.PendingReaderApplicationResponse;
import com.duanttcsn5.library.dto.librarycard.RejectReaderApplicationRequest;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.LibraryCardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/library-cards")
public class LibraryCardController {

    private final LibraryCardService libraryCardService;
    private final com.duanttcsn5.library.service.CardLockSuggestionService suggestions;

    public LibraryCardController(LibraryCardService libraryCardService,
            com.duanttcsn5.library.service.CardLockSuggestionService suggestions) {
        this.libraryCardService = libraryCardService;
        this.suggestions = suggestions;
    }


    @GetMapping("/lock-suggestions/settings")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Settings suggestionSettings() {
        return suggestions.settings();
    }
    @org.springframework.web.bind.annotation.PutMapping("/lock-suggestions/settings")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Settings updateSuggestionSettings(
            @Valid @RequestBody com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.UpdateSettings request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return suggestions.updateSettings(request,principal.id());
    }
    @GetMapping("/lock-suggestions")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Page lockSuggestions(
            @RequestParam(defaultValue="false") boolean history,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return suggestions.list(history,page,size);
    }
    @GetMapping("/lock-suggestions/{id}")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Detail suggestionDetail(@PathVariable Long id) {
        return suggestions.detail(id);
    }
    @PostMapping("/lock-suggestions/{id}/accept")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Decision acceptSuggestion(@PathVariable Long id,
            @Valid @RequestBody com.duanttcsn5.library.dto.librarycard.LockLibraryCardRequest request,
            @AuthenticationPrincipal UserPrincipal principal,HttpServletRequest servletRequest) {
        return suggestions.accept(id,request,principal.id(),servletRequest.getRemoteAddr());
    }
    @PostMapping("/lock-suggestions/{id}/ignore")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Decision ignoreSuggestion(@PathVariable Long id,
            @Valid @RequestBody com.duanttcsn5.library.dto.librarycard.CardLockSuggestionDtos.Ignore request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return suggestions.ignore(id,request,principal.id());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<PendingReaderApplicationResponse>> getPendingApplications(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(libraryCardService.getPendingApplications(search, fromDate, toDate));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<List<LibraryCardResponse>> getIssuedCards() {
        return ResponseEntity.ok(libraryCardService.getIssuedCards());
    }

    @PostMapping("/{readerUserId}/approve")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LibraryCardResponse> approve(
            @PathVariable Long readerUserId,
            @Valid @RequestBody ApproveLibraryCardRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        LibraryCardResponse response = libraryCardService.approve(
                readerUserId,
                request,
                principal.id(),
                servletRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{readerUserId}/reject")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<Void> reject(
            @PathVariable Long readerUserId,
            @Valid @RequestBody RejectReaderApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest servletRequest) {
        libraryCardService.reject(
                readerUserId,
                request,
                principal.id(),
                servletRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{cardId}/lock")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LibraryCardResponse> lockCard(@PathVariable Long cardId,
            @Valid @RequestBody com.duanttcsn5.library.dto.librarycard.LockLibraryCardRequest request,
            @AuthenticationPrincipal UserPrincipal principal, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(libraryCardService.lockCard(cardId, request,
                principal.id(), servletRequest.getRemoteAddr()));
    }

    @GetMapping("/{cardId}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<com.duanttcsn5.library.dto.librarycard.LibraryCardDetailsResponse> getCardDetails(
            @PathVariable Long cardId) {
        return ResponseEntity.ok(libraryCardService.getCardDetails(cardId));
    }

    @PostMapping("/{cardId}/unlock")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER', 'ADMIN')")
    public ResponseEntity<LibraryCardResponse> unlockCard(@PathVariable Long cardId,
            @Valid @RequestBody com.duanttcsn5.library.dto.librarycard.UnlockLibraryCardRequest request,
            @AuthenticationPrincipal UserPrincipal principal, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(libraryCardService.unlockCard(cardId, request,
                principal.id(), servletRequest.getRemoteAddr()));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('READER')")
    public ResponseEntity<MyLibraryCardResponse> getMyCard(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(libraryCardService.getMyCard(principal.id()));
    }
}
