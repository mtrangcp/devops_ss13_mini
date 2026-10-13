package vn.edu.ptit.campushub.returnrequest;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.campushub.returnrequest.ReturnRequestDtos.CreateReturnRequest;
import vn.edu.ptit.campushub.returnrequest.ReturnRequestDtos.ReturnRequestView;
import vn.edu.ptit.campushub.returnrequest.ReturnRequestDtos.StatusUpdateRequest;

@RestController
public class ReturnRequestController {
    private final ReturnRequestService service;

    public ReturnRequestController(ReturnRequestService service) {
        this.service = service;
    }

    @PostMapping("/api/posts/{postId}/return-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnRequestView create(@PathVariable Long postId,
                                   @Valid @RequestBody CreateReturnRequest request,
                                   Principal principal) {
        return service.create(postId, request, principal);
    }

    @GetMapping("/api/posts/{postId}/return-requests")
    public List<ReturnRequestView> findByPost(@PathVariable Long postId,
                                             Principal principal) {
        return service.findByPost(postId, principal);
    }

    @GetMapping("/api/return-requests/mine")
    public List<ReturnRequestView> findMine(Principal principal) {
        return service.findMine(principal);
    }

    @PatchMapping("/api/return-requests/{requestId}/status")
    public ReturnRequestView updateStatus(@PathVariable Long requestId,
                                          @Valid @RequestBody StatusUpdateRequest request,
                                          Principal principal) {
        return service.updateStatus(requestId, request, principal);
    }
}
