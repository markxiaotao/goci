package uk.ac.ebi.spot.goci.service;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import uk.ac.ebi.spot.goci.model.Study;
import uk.ac.ebi.spot.goci.service.exception.PubmedLookupException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Properties;


/**
 * A GociPubMedDispatcher service that loads PubMed query strings from a properties file, application.properties.  Upon
 * construction, this dispatcher is ready to dispatch requests manually, but will only do so periodically once the
 * startDispatcher() method is called.  The startDispatcher() method does a limited amount of initialisation, only
 * ensuring that queries are dispatched regularly.  If you are using this service to dispatch queries manually, you do
 * not need to call startDispatcher().
 *
 * @author Tony Burdett
 *         Date 26/10/11
 */
@Service
public class PropertyFilePubMedDispatcherService implements GwasPubMedDispatcherService {
    public static final int PUBMED_MAXRECORDS = 1000;

    private HttpContext httpContext;
    private HttpClient httpClient;
    private String summaryString;

    // xml version is very important here , it must be "&version=2.0"
    private String xmlVersion;


    private Logger log = LoggerFactory.getLogger(getClass());

    public PropertyFilePubMedDispatcherService() {
        this.httpContext = new BasicHttpContext();
        this.httpContext.setAttribute(ClientContext.COOKIE_STORE, new BasicCookieStore());
        this.httpClient = new DefaultHttpClient();

        Properties properties = new Properties();

        try {
            properties.load(getClass().getClassLoader().getResource("application.properties").openStream());
            this.summaryString = properties.getProperty("pubmed.root")
                    .concat(properties.getProperty("pubmed.gwas.summary"));
            this.xmlVersion = properties.getProperty("pubmed.xml.version");
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to create dispatcher service: failed to read application.properties resource", e);
        }
    }


    public Study dispatchSummaryQuery(String pubmedId) throws PubmedLookupException {

        Document response = null;

        // Run query and create study object
        try {
            response = doPubmedQuery(URI.create(summaryString.replace("{idlist}", pubmedId) + xmlVersion));

            NodeList docSumNodes = response.getElementsByTagName("DocumentSummary");

            // The document summary is present then we should have a publication
            if (docSumNodes.getLength() > 0) {
                Study newStudy = new Study();

                // Assuming here we only have one document summary as pubmed id should correspond to one publication
                Node docSumNode = docSumNodes.item(0);

                // Initialize study attributes
                String pmid = null;
                String title = "";
                Date pubDate = null;
                String author = "";
                String publication = "";


                // get the ID element (should only be one, take first regardless)
                Element study = (Element) docSumNode;

                pmid = study.getAttribute("uid");

                if (study.getElementsByTagName("error").item(0) != null) {

                    author = null;
                    title = null;
                    publication = null;
                    pubDate = null;

                } else {

                    title = study.getElementsByTagName("Title").item(0).getTextContent();
                    publication = study.getElementsByTagName("Source").item(0).getTextContent();
                    author = study.getElementsByTagName("SortFirstAuthor").item(0).getTextContent();

                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");

                    String date = study.getElementsByTagName("SortPubDate").item(0).getTextContent();

                    if (date.contains("/")) {
                        date = date.replace("/", "-");
                    }

                    java.util.Date studyDate = null;
                    try {
                        studyDate = format.parse(date);
                    } catch (ParseException e1) {
                        e1.printStackTrace();
                    }
                    pubDate = new Date(studyDate.getTime());
                }


                if (pmid != null) {

                    if (author != null && pubDate != null && publication != null && title != null) {

                        newStudy.setAuthor(author);
                        newStudy.setPubmedId(pmid);
                        newStudy.setPublication(publication);
                        newStudy.setTitle(title);
                        newStudy.setStudyDate(pubDate);
                    }
                }

                return newStudy;
            } else {
                throw new PubmedLookupException("Couldn't find pubmed id " + pubmedId + " in PubMed");
            }
        } catch (IOException e) {
            throw new PubmedLookupException("Couldn't find pubmed id " + pubmedId + " in PubMed", e);
        }

    }

    private Document doPubmedQuery(URI queryUri) throws IOException {
        HttpGet httpGet = new HttpGet(queryUri);
        HttpResponse response = httpClient.execute(httpGet, httpContext);
        HttpEntity entity = response.getEntity();
        InputStream entityIn = entity.getContent();

        try {
            if (response.getStatusLine().getStatusCode() == HttpStatus.OK.value()) {
                try {
                    DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                    return db.parse(entityIn);
                } catch (SAXException e) {
                    throw new IOException("Could not parse response from PubMed due to an exception reading content",
                            e);
                } catch (ParserConfigurationException e) {
                    throw new IOException("Could not parse response from PubMed due to an exception reading content",
                            e);
                }
            } else {
                throw new IOException(
                        "Could not obtain results from '" + queryUri + "' due to an unknown communication problem");
            }
        } finally {
            entityIn.close();
        }
    }
}
