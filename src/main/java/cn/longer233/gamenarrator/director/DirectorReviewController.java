package cn.longer233.gamenarrator.director;
import org.springframework.web.bind.annotation.*;
import java.util.List;import java.util.UUID;
@RestController @RequestMapping("/api/tasks/{taskId}/director-reviews")
public class DirectorReviewController {private final DirectorReviewService service;public DirectorReviewController(DirectorReviewService service){this.service=service;}
 @GetMapping public List<DirectorReviewView> list(@PathVariable UUID taskId){return service.list(taskId);}
 @PostMapping public DirectorReviewView start(@PathVariable UUID taskId,@RequestBody(required=false) StartRequest body){return service.start(taskId,body==null?1:body.rounds());}
 @PostMapping("/{reviewId}/decision") public DirectorReviewView decide(@PathVariable UUID reviewId,@RequestBody DecisionRequest body){return service.decide(reviewId,body.action(),body.modification());}
 @PostMapping("/{reviewId}/apply") public DirectorReviewView apply(@PathVariable UUID reviewId){return service.apply(reviewId);}
 public record StartRequest(int rounds){} public record DecisionRequest(String action,String modification){}
}
