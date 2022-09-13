/*
 *
 * Copyright 2019 Johns Hopkins University
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.dataconservancy.pass.doi.service;

import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.stream.JsonParsingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.pass.object.model.Journal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@WebServlet(urlPatterns = "/journal")
public class PassDoiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(PassDoiServlet.class);

    //longest time we expect it should take to create a Journal object, in ms
    private int cachePeriod = 30000;//milliseconds
    private OkHttpClient client;

    private String BASE_URL = "https://api.crossref.org/";
    private String VERSION = "v1/";
    private String BASIC_PREFIX = "works/";
    //some defaults
    private String MAILTO = "pass@jhu.edu";
    private String FEDORA_INTERNAL = "http://fcrepo:8080/fcrepo/rest/";
    private String FEDORA_EXTERNAL = "https://pass.local/fcrepo/rest/";

    private Set<String> activeJobs = new HashSet<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(30, SECONDS);
        builder.readTimeout(30, SECONDS);
        builder.writeTimeout(30, SECONDS);
        client = builder.build();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        LOG.info("Servicing new request ... ");
        LOG.debug("Context path: " + request.getContextPath() + "; query string " + request.getQueryString());

        //we will call out to crossref and collect the work JSON object
        //the value of this parameter is expected to be already URIencoded
        String doi = request.getParameter("doi");

        //stage 1: verify doi is valid
        if (verify(doi) == null) {
            // do not have have a valid xref doi
            try (OutputStream out = response.getOutputStream()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", "Supplied DOI is not in valid Crossref format.")
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(400);
                return;
            }
        }

        //stage 2: check cache map for existence of doi
        //put doi on map if absent
        if (activeJobs.contains(doi)) {
            try (OutputStream out = response.getOutputStream()) {
                String message = "There is already an active request for " + doi;
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message + "; try again later.")
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(429);
                LOG.info(message);
                return;
            }

        } else {
            // this DOI is not actively being processed
            // let's temporarily prohibit new requests for this DOI
            activeJobs.add(doi);
            Thread t = new Thread(new ExpiringLock(doi, cachePeriod));
            t.start();
        }

        //stage 3: try to get crossref record, catch errors first, and halt processing
        JsonObject xrefJsonObject = retrieveXrefMetdata(doi);
        if (xrefJsonObject == null) {
            try (OutputStream out = response.getOutputStream()) {
                String message = "There was an error getting the metadata from Crossref for " + doi;
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(500);
                LOG.info(message);
            }
        } else if (xrefJsonObject.getJsonString("error") != null) {
            int responseCode;
            String message;
            if (xrefJsonObject.getString("error").equals("Resource not found.")) {
                responseCode = 404;
                message = "The resource for DOI " + doi + " could not be found on Crossref.";
            } else {
                responseCode = 500;
                message = "A record for this resource could not be found on Crossref: " +
                          xrefJsonObject.getJsonString("error");
            }
            try (OutputStream out = response.getOutputStream()) {
                JsonObject jsonObject = Json.createObjectBuilder()
                                            .add("error", message)
                                            .build();
                out.write(jsonObject.toString().getBytes());
                response.setStatus(responseCode);
                LOG.info(message);
            }
        } else {
            // have a non-empty string to process
            LOG.debug("Building pass journal");
            // we probably have something JSONy at this point. Let's build a journal object from it
            Journal journal = buildPassJournal(xrefJsonObject);
            LOG.debug("Comparing journal object with possible PASS version");
            // and compare it with what we already have in PASS, updating PASS if necessary

            Journal updatedJournal = updateJournalInPass(journal);

            String journalId = null;

            if (updatedJournal != null) {
                journalId = updatedJournal.getId().toString();
            }

            if (journalId != null) {

                try (OutputStream out = response.getOutputStream()) {
                    JsonObject jsonObject = Json.createObjectBuilder()
                                                .add("journal-id", journalId)
                                                .add("crossref", xrefJsonObject)
                                                .build();

                    out.write(jsonObject.toString().getBytes());
                    response.setStatus(200);
                    LOG.info("Returning result for DOI " + doi);
                }
            } else {
                // journal id is null - this should never happen unless Crosssref journal is insufficient
                // for example, if a book doi ws supplied which has no issns

                try (OutputStream out = response.getOutputStream()) {
                    String message = "Insufficient information to locate or specify a journal entry.";
                    JsonObject jsonObject = Json.createObjectBuilder()
                                                .add("error", message)
                                                .build();

                    out.write(jsonObject.toString().getBytes());
                    response.setStatus(422);
                    LOG.info(message);
                }
            }
        }
        activeJobs.remove(doi);
    }

    /**
     * consult crossref to get a works object for a supplied doi
     *
     * @param doi - the supplied doi string, prefix trimmed if necessary
     * @return a string representing the works object if successful; an empty string if not found; null if IO exception
     */
    JsonObject retrieveXrefMetdata(String doi) {
        String agent = System.getenv("PASS_DOI_SERVICE_MAILTO") != null ? System.getenv(
            "PASS_DOI_SERVICE_MAILTO") : MAILTO;

        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + VERSION + BASIC_PREFIX + doi).newBuilder();
        String url = urlBuilder.build().toString();
        Request okHttpRequest = new Request.Builder()
            .url(url)
            .addHeader("User-Agent", agent)
            .build();
        Call call = client.newCall(okHttpRequest);
        JsonReader reader;
        JsonObject xrefJsonObject;
        String responseString = null;

        try (Response okHttpResponse = call.execute()) {
            responseString = okHttpResponse.body().string();
            reader = Json.createReader(new StringReader(responseString));
            xrefJsonObject = reader.readObject();
            reader.close();
            return xrefJsonObject;
        } catch (JsonParsingException e) {
            if (responseString != null) {
                return Json.createObjectBuilder()
                           .add("error", responseString)
                           .build();
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * Takes JSON which represents journal article metadata from Crossref
     * and populates a new Journal object. Currently we take typed issns and the journal
     * name.
     *
     * @param metadata - the JSON metadata from Crossref
     * @return the PASS journal object
     */
    Journal buildPassJournal(JsonObject metadata) {

        LOG.debug("JSON input (from Crossref): " + metadata.toString());

        final String XREF_MESSAGE = "message";
        final String XREF_TITLE = "container-title";
        final String XREF_ISSN_TYPE_ARRAY = "issn-type";
        final String XREF_ISSN_ARRAY = "ISSN";
        final String XREF_ISSN_TYPE = "type";
        final String XREF_ISSN_VALUE = "value";

        Journal passJournal = new Journal();

        JsonObject messageObject = metadata.getJsonObject(XREF_MESSAGE);
        JsonArray containerTitleArray = messageObject.getJsonArray(XREF_TITLE);
        JsonArray issnTypeArray = messageObject.getJsonArray(XREF_ISSN_TYPE_ARRAY);
        JsonArray issnArray = messageObject.getJsonArray(XREF_ISSN_ARRAY);

        if (!containerTitleArray.isNull(0)) {
            passJournal.setJournalName(containerTitleArray.getString(0));
        }

        Set<String> processedIssns = new HashSet<>();

        if (issnTypeArray != null) {
            for (int i = 0; i < issnTypeArray.size(); i++) {
                JsonObject issn = issnTypeArray.getJsonObject(i);

                String type = "";

                //translate crossref issn-type strings to PASS issn-type strings
                if (IssnType.PRINT.getCrossrefTypeString().equals(issn.getString(XREF_ISSN_TYPE))) {
                    type = IssnType.PRINT.getPassTypeString();
                } else if (IssnType.ELECTRONIC.getCrossrefTypeString().equals(issn.getString(XREF_ISSN_TYPE))) {
                    type = IssnType.ELECTRONIC.getPassTypeString();
                }

                //collect the value for this issn
                String value = issn.getString(XREF_ISSN_VALUE);
                processedIssns.add(value);

                if (value.length() > 0) {
                    passJournal.getIssns().add(String.join(":", type, value));
                    LOG.debug("Adding typed ISSN to journal object: " + String.join(":", type, value));
                }
            }
        }

        if (issnArray != null) {
            for (int i = 0; i < issnArray.size(); i++) {
                // if we have issns which were not given as typed, we add them without a type
                String issn = issnArray.getString(i);
                if (!processedIssns.contains(issn)) {
                    passJournal.getIssns().add(":" + issn);//do this to conform with type:value format
                }
            }
        }

        passJournal.setId(null); // we don't need this
        return passJournal;
    }

    /**
     * Take a Journal object constructed from Crossref metadata, and compare it with the
     * version of this object which we have in PASS. Construct the most complete Journal
     * object possible from the two sources - PASS objects are more authoritative. Use the
     * Crossref version if we don't have it already in PASS. Store the resulting object in PASS.
     *
     * @param journal - the Journal object generated from Crossref metadata
     * @return the updated Journal object stored in PASS if the PASS object needs updating; null if we don't have
     * enough info to create a journal
     */
    Journal updateJournalInPass(Journal journal) {
        LOG.debug("GETTING ISSNS");
        List<String> issns = journal.getIssns();
        LOG.debug("GETTING NAME");
        String name = journal.getJournalName();

        Journal passJournal = null;

        URI passJournalUri = find(name, issns);

        if (passJournalUri == null) {
            // we don't have this journal in pass yet
            if (name != null && !name.isEmpty() && issns.size() > 0) {
                // we have enough info to make a journal entry
                // passJournal = passClient.createAndReadResource(journal, Journal.class);
            } else {
                // do not have enough to create a new journal
                LOG.debug("Not enough info for journal " + name);
                return null;
            }
        } else { //we have a journal, let's see if we can add anything new - just issns atm. we add only if not present
            // passJournal = passClient.readResource(passJournalUri, Journal.class);

            if (passJournal != null) {
                //check to see if we can supply issns
                if (!passJournal.getIssns().containsAll(journal.getIssns())) {
                    List<String> newIssnList = Stream.concat(passJournal.getIssns().stream(),
                                                             journal.getIssns().stream()).distinct()
                                                     .collect(Collectors.toList());
                    passJournal.setIssns(newIssnList);
                    // passClient.updateResource(passJournal);
                }

            } else {
                String uhoh = "Journal URI " + passJournalUri.toString() + " was found, but the object could not be " +
                              "retrieved. This should never happen.";
                LOG.error(uhoh);
                throw new RuntimeException(uhoh);
            }

        }
        //externalize the internal journal id
        String internalPrefix = System.getenv("PASS_FEDORA_BASEURL") != null ? System.getenv(
            "PASS_FEDORA_BASEURL") : FEDORA_INTERNAL;
        String externalPrefix = System.getenv("PASS_EXTERNAL_FEDORA_BASEURL") != null ? System.getenv(
            "PASS_EXTERNAL_FEDORA_BASEURL") : FEDORA_EXTERNAL;
        internalPrefix = internalPrefix + (internalPrefix.endsWith("/") ? "" : "/");
        externalPrefix = externalPrefix + (externalPrefix.endsWith("/") ? "" : "/");
        LOG.debug("Internal prefix: " + internalPrefix);
        LOG.debug("External prefix: " + externalPrefix);
        String internalUriString = passJournal.getId().toString();
        if (internalUriString.startsWith(internalPrefix)) {
            // passJournal.setId(URI.create(internalUriString.replace(internalPrefix, externalPrefix)));
        }
        LOG.debug("passJournal URI: " + passJournal.getId().toString());
        LOG.debug("Returning journal object: " + passJournal.toString());
        return passJournal;
    }

    /**
     * Find a journal in our repository. We take the best match we can find. finder algorithm here should harmonize
     * with the approach in the {@code BatchJournalFinder} in the journal loader code
     *
     * @param name  the name of the journal to be found
     * @param issns the set of issns to find. we assume that the issns stored in the repo are of the format type:value
     * @return the URI of the best match, or null in nothing matches
     */
    URI find(String name, List<String> issns) {

        Set<URI> nameUriSet = new HashSet<>();
        // TODO passClient.findAllByAttribute(Journal.class, "name", name);
        Map<URI, Integer> uriScores = new HashMap<>();

        if (!issns.isEmpty()) {
            for (String issn : issns) {
                Set<URI> issnList = null;
                // TODO passClient.findAllByAttribute(Journal.class, "issns", issn);
                if (issnList != null) {
                    for (URI uri : issnList) {
                        Integer i = uriScores.putIfAbsent(uri, 1);
                        if (i != null) {
                            uriScores.put(uri, i + 1);
                        }
                    }
                }
            }
        }

        if (nameUriSet != null) {
            for (URI uri : nameUriSet) {
                Integer i = uriScores.putIfAbsent(uri, 1);
                if (i != null) {
                    uriScores.put(uri, i + 1);
                }
            }
        }

        if (uriScores.size() > 0) {
            // we have matches, pick the best one
            Integer highScore = Collections.max(uriScores.values());

            int minimumQualifyingScore = 1;
            // with so little to go on, we may realistically get just one hit

            List<URI> sortedUris = new ArrayList<>();

            for (int i = highScore; i >= minimumQualifyingScore; i--) {
                for (URI uri : uriScores.keySet()) {
                    if (uriScores.get(uri) == i) {
                        sortedUris.add(uri);
                    }
                }
            }

            if (sortedUris.size() > 0) {
                // there are matching journals
                // return the best match
                return sortedUris.get(0);
            }
        }

        // nothing matches, create a new journal
        return null;
    }

    /**
     * check to see whether supplied DOI is in Crossref format after splitting off a possible prefix
     *
     * @return the valid suffix, or null if invalid
     */
    String verify(String doi) {
        if (doi == null) {
            return null;
        }
        String criterion = "doi.org/";
        int i = doi.indexOf(criterion);
        String suffix = i >= 0 ? doi.substring(i + criterion.length()) : doi;

        Pattern pattern = Pattern.compile("^10\\.\\d{4,9}/[-._;()/:a-zA-Z0-9]+$");

        Matcher matcher = pattern.matcher(suffix);
        return matcher.matches() ? suffix : null;
    }

    /**
     * A class to manage locking so that an active process for a DOI will finish executing before
     * another one begins
     */
    public class ExpiringLock implements Runnable {
        private String key;
        private int duration;

        ExpiringLock(String key, int duration) {
            this.key = key;
            this.duration = duration;
        }

        public void run() {
            try {
                sleep(duration);
                activeJobs.remove(key);
            } catch (InterruptedException e) {
                activeJobs.remove(key);
            }
        }

    }

    /**
     * a convenienc enum for translating type strings for issns
     */
    public enum IssnType {
        PRINT,
        ELECTRONIC;

        private String passTypeString;

        static {
            // these values represent how types are stored on the issn field for the PASS Journal object
            PRINT.passTypeString = "Print";
            ELECTRONIC.passTypeString = "Online";
        }

        public String getPassTypeString() {
            return passTypeString;
        }

        private String crossrefTypeString;

        static {
            // these values represent how issn types are presented in Crossref metadata
            PRINT.crossrefTypeString = "print";
            ELECTRONIC.crossrefTypeString = "electronic";
        }

        public String getCrossrefTypeString() {
            return crossrefTypeString;
        }
    }
}
