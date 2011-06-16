package org.opennms.netmgt.poller.monitors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opennms.core.test.JUnitDNSServerExecutionListener;
import org.opennms.core.test.annotations.DNSEntry;
import org.opennms.core.test.annotations.DNSZone;
import org.opennms.core.test.annotations.JUnitDNSServer;
import org.opennms.netmgt.dao.db.OpenNMSConfigurationExecutionListener;
import org.opennms.netmgt.model.PollStatus;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.ServiceMonitor;
import org.opennms.test.mock.MockLogAppender;
import org.opennms.test.mock.MockUtil;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;


@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({
    OpenNMSConfigurationExecutionListener.class,
    TransactionalTestExecutionListener.class,
    JUnitDNSServerExecutionListener.class
})
@ContextConfiguration(locations={"classpath:/META-INF/opennms/emptyContext.xml"})
@JUnitDNSServer(port=9153, zones={
            @DNSZone(name="example.com", entries={
                    @DNSEntry(hostname="test", address="192.168.0.1")
            }),
            @DNSZone(name="ipv6.example.com", entries= {
                    @DNSEntry(hostname="ipv6test", address="2001:4860:8007::63", ipv6=true)
            })
    })
public class DnsMonitorTest {
    
    @Before
    public void setup() throws Exception {
        MockLogAppender.setupLogging();
    }
    
    @Test
    public void testIPV6Response() throws UnknownHostException {
        final Map<String, Object> m = Collections.synchronizedMap(new TreeMap<String, Object>());

        final ServiceMonitor monitor = new DnsMonitor();
        final MonitoredService svc = MonitorTestUtils.getMonitoredService(99, "::1", "DNS", true);

        m.put("port", "9153");
        m.put("retry", "1");
        m.put("timeout", "1000");
        m.put("lookup", "ipv6.example.com");
        
        final PollStatus status = monitor.poll(svc, m);
        MockUtil.println("Reason: "+status.getReason());
        assertEquals(PollStatus.SERVICE_AVAILABLE, status.getStatusCode());
    }
    
    @Test
    // type not found is still considered a valid response with the default response codes
    public void testNotFound() throws UnknownHostException {
        final Map<String, Object> m = Collections.synchronizedMap(new TreeMap<String, Object>());

        final ServiceMonitor monitor = new DnsMonitor();
        final MonitoredService svc = MonitorTestUtils.getMonitoredService(99, "::1", "DNS", true);

        m.put("port", "9153");
        m.put("retry", "2");
        m.put("timeout", "5000");
        m.put("lookup", "bogus.example.com");
        
        final PollStatus status = monitor.poll(svc, m);
        MockUtil.println("Reason: "+status.getReason());
        assertEquals(PollStatus.SERVICE_AVAILABLE, status.getStatusCode());
    }
    
    @Test
    // type not found is still considered a valid response with the default response codes
    public void testNotFoundWithCustomRcode() throws UnknownHostException {
        final Map<String, Object> m = Collections.synchronizedMap(new TreeMap<String, Object>());

        final ServiceMonitor monitor = new DnsMonitor();
        final MonitoredService svc = MonitorTestUtils.getMonitoredService(99, "::1", "DNS", true);

        m.put("port", "9153");
        m.put("retry", "2");
        m.put("timeout", "5000");
        m.put("lookup", "bogus.example.com");
        m.put("fatal-response-codes", "3");
        
        final PollStatus status = monitor.poll(svc, m);
        MockUtil.println("Reason: "+status.getReason());
        assertEquals(PollStatus.SERVICE_UNAVAILABLE, status.getStatusCode());
    }
    
    @Test
    public void testUnrecoverable() throws UnknownHostException {
        final Map<String, Object> m = Collections.synchronizedMap(new TreeMap<String, Object>());

        final ServiceMonitor monitor = new DnsMonitor();
        final MonitoredService svc = MonitorTestUtils.getMonitoredService(99, "192.168.1.120", "DNS", false);

        m.put("port", "9000");
        m.put("retry", "2");
        m.put("timeout", "500");
        
        final PollStatus status = monitor.poll(svc, m);
        MockUtil.println("Reason: "+status.getReason());
        assertEquals(PollStatus.SERVICE_UNAVAILABLE, status.getStatusCode());
    }
    
    @Test
    public void testDNSIPV4Response() throws UnknownHostException {
        final Map<String, Object> m = Collections.synchronizedMap(new TreeMap<String, Object>());

        final ServiceMonitor monitor = new DnsMonitor();
        final MonitoredService svc = MonitorTestUtils.getMonitoredService(99, "127.0.0.1", "DNS", false);

        m.put("port", "9153");
        m.put("retry", "1");
        m.put("timeout", "3000");
        m.put("lookup", "example.com");
        
        final PollStatus status = monitor.poll(svc, m);
        MockUtil.println("Reason: "+status.getReason());
        assertEquals(PollStatus.SERVICE_AVAILABLE, status.getStatusCode());
    }
    
    @Test
    public void testDnsJavaResponse() throws IOException {
        final Lookup l = new Lookup("example.com");
        final SimpleResolver resolver = new SimpleResolver("127.0.0.1");
        resolver.setPort(9153);
        l.setResolver(resolver);
        l.run();
        
        System.out.println("result: " + l.getResult());
        if(l.getResult() == Lookup.SUCCESSFUL) {
            System.out.println(l.getAnswers()[0].rdataToString());
        }
        assertTrue(l.getResult() == Lookup.SUCCESSFUL);
    }
    
    @Test
    public void testDnsJavaQuadARecord() throws IOException {
        final Lookup l = new Lookup("ipv6.example.com", Type.AAAA);
        final SimpleResolver resolver = new SimpleResolver("::1");
        resolver.setPort(9153);
        l.setResolver(resolver);
        l.run();
        
        System.out.println("result: " + l.getResult());
        if(l.getResult() == Lookup.SUCCESSFUL) {
            System.out.println(l.getAnswers()[0].rdataToString());
        }
        assertTrue(l.getResult() == Lookup.SUCCESSFUL);
    }
    
    @Test
    public void testDnsJavaWithDnsServer() throws TextParseException, UnknownHostException {
        final Lookup l = new Lookup("example.com", Type.AAAA);
        final SimpleResolver resolver = new SimpleResolver("::1");
        resolver.setPort(9153);
        l.setResolver(resolver);
        l.run();
        
        System.out.println("result: " + l.getResult());
        final Record[] answers = l.getAnswers();
        assertEquals(answers.length, 1);
        
        final Record record = answers[0];
        System.err.println(record.getTTL());
        
        if(l.getResult() == Lookup.SUCCESSFUL) {
            System.out.println(l.getAnswers()[0].rdataToString());
        }
        assertTrue(l.getResult() == Lookup.SUCCESSFUL);
    }

    @Test
    @JUnitDNSServer(port=9153, zones={})
    public void testNoAnswer() throws Exception {
        final Lookup l = new Lookup("example.com", Type.AAAA);
        final SimpleResolver resolver = new SimpleResolver("::1");
        resolver.setPort(9153);
        l.setResolver(resolver);
        l.run();
        
        System.out.println("result: " + l.getResult());
        final Record[] answers = l.getAnswers();
        assertEquals(answers.length, 1);
        
        final Record record = answers[0];
        System.err.println(record.getTTL());
        
        if(l.getResult() == Lookup.SUCCESSFUL) {
            System.out.println(l.getAnswers()[0].rdataToString());
        }
        assertTrue(l.getResult() == Lookup.SUCCESSFUL);
    }
}
