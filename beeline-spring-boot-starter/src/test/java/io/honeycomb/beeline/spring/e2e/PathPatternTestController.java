package io.honeycomb.beeline.spring.e2e;

import io.honeycomb.beeline.tracing.Beeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PathPatternTestController {
    @Autowired
    private Beeline beeline;

    @GetMapping("/allowlist/basic-get")
    @ResponseBody
    public String basicGetAllowlist() {
        beeline.getActiveSpan().addField("endpoint", "allowlist");
        return "hello";
    }

    @GetMapping("/denylist/basic-get")
    @ResponseBody
    public String basicGetDenylist() {
        beeline.getActiveSpan().addField("endpoint", "denylist");
        return "olleh";
    }

    @GetMapping("/allowlist/forward-to-denylist")
    public String forwardToDenylist() {
        beeline.getActiveSpan().addField("endpoint", "forward");
        return "forward:/denylist/basic-get";
    }
}
