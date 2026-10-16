package com.duanttcsn5.library.dto.librarycard;

import java.util.List;

public record LibraryCardDetailsResponse(LibraryCardResponse card, String lockActorName,
        List<LibraryCardLockHistoryResponse> history) {}
