package org.dataconservancy.pass.doi.service;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.pass.object.model.Journal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.neo4j.Neo4jProperties.Authentication;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.yahoo.elide.RefreshableElide;
import com.yahoo.elide.spring.config.ElideConfigProperties;

@RestController
@Configuration
@RefreshScope
public class JournalDoiController {
    private final PassClient passClient;

    @Autowired
    public JournalDoiController(RefreshableElide refreshableElide, ElideConfigProperties settings) {
        this.passClient = new ElidePassClient2(refreshableElide, settings);
    }

    @GetMapping(value = "/doi", produces = MediaType.TEXT_PLAIN_VALUE)
    public String process(@RequestHeader HttpHeaders requestHeaders,
            @RequestParam MultiValueMap<String, String> allRequestParams, HttpServletRequest request,
            Authentication authentication) throws IOException {

        // User user = new AuthenticationUser(authentication);

        {
            Journal j = new Journal();
            j.setJournalName("This is the journal that does not end");
            j.setNlmta("hmm");

            passClient.createObject(j);
        }

        StringBuilder result = new StringBuilder("These are the journals: ");

        passClient.visitObjects(Journal.class, j -> {
            result.append(j.getId() + " " + j.getJournalName() + "\n");
        });

        return result.toString();
    }
}
