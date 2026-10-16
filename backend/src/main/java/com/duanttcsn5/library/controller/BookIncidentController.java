package com.duanttcsn5.library.controller;

import com.duanttcsn5.library.dto.incident.IncidentDtos.*;
import com.duanttcsn5.library.security.UserPrincipal;
import com.duanttcsn5.library.service.BookIncidentService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/book-incidents")
public class BookIncidentController {
    private final BookIncidentService service;
    public BookIncidentController(BookIncidentService service) { this.service=service; }
    @GetMapping("/factors")
    @PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
    public Factors factors() { return service.factors(); }
    @PutMapping("/factors")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER','ADMIN')")
    public Factors update(@RequestBody Factors factors,@AuthenticationPrincipal UserPrincipal actor) {
        return service.updateFactors(factors,actor.id());
    }
    @GetMapping("/open-items")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public List<Item> search(@RequestParam(defaultValue="") String query,@AuthenticationPrincipal UserPrincipal actor) {
        return service.search(query,actor.id());
    }
    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public Preview preview(@RequestParam(required=false) Long itemId,@RequestParam(required=false) String type,@AuthenticationPrincipal UserPrincipal actor) {
        return service.preview(itemId,type,actor.id());
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    public ResponseEntity<Incident> confirm(@RequestParam(required=false) Long itemId,@RequestParam(required=false) String type,
            @RequestParam(required=false) String description,@RequestParam(required=false) String previewToken,
            @RequestParam(value="files",required=false) List<MultipartFile> files,
            @RequestParam(required=false) String proposedCompensationVnd,@RequestParam(required=false) String adjustmentReason,
            @AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.confirm(itemId,type,description,previewToken,files,actor.id(),proposedCompensationVnd,adjustmentReason));
    }
    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
    public List<Incident> history(@RequestParam(defaultValue="") String query,@AuthenticationPrincipal UserPrincipal actor) {
        return service.history(query,actor.id());
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
    public Incident detail(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal actor) { return service.detail(id,actor.id()); }
    @GetMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
    public ResponseEntity<byte[]> image(@PathVariable Long id,@PathVariable Long imageId,@AuthenticationPrincipal UserPrincipal actor) {
        Image image=service.image(id,imageId,actor.id());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").body(image.data());
    }
    @GetMapping("/{id}/adjustments")
    @PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
    public List<Adjustment> adjustments(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal actor) {
        return service.adjustments(id,actor.id());
    }
    @GetMapping("/{id}/adjustment-preview")
    @PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
    public AdjustmentPreview adjustmentPreview(@PathVariable Long id,@RequestParam String targetStatus,@AuthenticationPrincipal UserPrincipal actor) {
        return service.adjustmentPreview(id,targetStatus,actor.id());
    }
    @PostMapping("/{id}/adjustments")
    @PreAuthorize("hasAnyRole('LIBRARIAN','LIBRARY_MANAGER','ADMIN')")
    public ResponseEntity<Adjustment> requestAdjustment(@PathVariable Long id,@RequestBody AdjustmentRequest request,@AuthenticationPrincipal UserPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestAdjustment(id,request,actor.id()));
    }
    @PostMapping("/{id}/adjustments/{adjustmentId}/confirm")
    @PreAuthorize("hasAnyRole('LIBRARY_MANAGER','ADMIN')")
    public Adjustment confirmAdjustment(@PathVariable Long id,@PathVariable Long adjustmentId,@AuthenticationPrincipal UserPrincipal actor) {
        return service.confirmAdjustment(id,adjustmentId,actor.id());
    }
}
