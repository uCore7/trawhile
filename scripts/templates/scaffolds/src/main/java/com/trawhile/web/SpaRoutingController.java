package com.trawhile.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards all non-API, non-static paths to index.html so the Angular router
 * can handle client-side navigation on hard refresh.
 */
@Controller
public class SpaRoutingController {

    @GetMapping(value = {"/{path:[^\\.]*}", "/{path:^(?!api).*}/**/{sub:[^\\.]*}"})
    public String index() {
        return "forward:/index.html";
    }
}
