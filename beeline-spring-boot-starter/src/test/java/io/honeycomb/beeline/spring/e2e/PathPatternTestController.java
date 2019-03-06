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

    @GetMapping("/whitelist/basic-get")
    @ResponseBody
    public String basicGetWhitelist() {
        beeline.getActiveSpan().addField("endpoint", "whitelist");
        return "hello";
    }

    @GetMapping("/blacklist/basic-get")
    @ResponseBody
    public String basicGetBlacklist() {
        beeline.getActiveSpan().addField("endpoint", "blacklist");
        return "olleh";
    }

    @GetMapping("/whitelist/forward-to-blacklist")
    public String forwardToBlacklist() {
        beeline.getActiveSpan().addField("endpoint", "forward");
        return "forward:/blacklist/basic-get";
    }
}
