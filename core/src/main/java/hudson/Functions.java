package hudson;

import hudson.maven.ExecutedMojo;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.TopLevelItem;
import hudson.model.View;
import org.apache.commons.jexl.parser.ASTSizeFunction;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.MonitorInfo;
import java.lang.management.LockInfo;

/**
 * Utility functions used in views.
 *
 * <p>
 * An instance of this class is created for each request.
 *
 * @author Kohsuke Kawaguchi
 */
public class Functions {
    private static volatile int globalIota = 0;
    private int iota;

    public Functions() {
        iota = globalIota;
        // concurrent requests can use the same ID --- we are just trying to
        // prevent the same user from seeing the same ID repeatedly.
        globalIota+=1000;
    }

    /**
     * Generates an unique ID.
     */
    public String generateId() {
        return "id"+iota++;
    }

    public static boolean isModel(Object o) {
        return o instanceof ModelObject;
    }

    public static String xsDate(Calendar cal) {
        return Util.XS_DATETIME_FORMATTER.format(cal.getTime());
    }

    public static String rfc822Date(Calendar cal) {
        return Util.RFC822_DATETIME_FORMATTER.format(cal.getTime());
    }

    /**
     * Prints the integer as a string that represents difference,
     * like "-5", "+/-0", "+3".
     */
    public static String getDiffString(int i) {
        if(i==0)    return "\u00B10";   // +/-0
        String s = Integer.toString(i);
        if(i>0)     return "+"+s;
        else        return s;
    }

    /**
     * {@link #getDiffString2(int)} that doesn't show anything for +/-0
     */
    public static String getDiffString2(int i) {
        if(i==0)    return "";
        String s = Integer.toString(i);
        if(i>0)     return "+"+s;
        else        return s;
    }

    /**
     * Adds the proper suffix.
     */
    public static String addSuffix(int n, String singular, String plural) {
        StringBuffer buf = new StringBuffer();
        buf.append(n).append(' ');
        if(n==1)
            buf.append(singular);
        else
            buf.append(plural);
        return buf.toString();
    }

    public static RunUrl decompose(StaplerRequest req) {
        @SuppressWarnings("unchecked") // pre-JDK 5 API?
        List<Ancestor> ancestors = req.getAncestors();
        for (Ancestor anc : ancestors) {
            if(anc.getObject() instanceof Run) {
                // bingo
                String ancUrl = anc.getUrl();

                String reqUri = req.getOriginalRequestURI();
                // despite the spec saying this string is not decoded,
                // Tomcat apparently decodes this string. You see ' ' instead of '%20', which is what
                // the browser has sent. So do some quick scan to see if it's ASCII safe, and if not
                // re-encode it. Otherwise it won't match with ancUrl.
                if(reqUri.indexOf(' ')>=0) {
                    try {
                        // 3 arg version accepts illegal character. 1-arg version doesn't
                        reqUri = new URI(null,reqUri,null).toASCIIString();
                    } catch (URISyntaxException e) {
                        // try to use reqUri as is.
                    }
                }

                return new RunUrl(
                    (Run) anc.getObject(), ancUrl,
                    reqUri.substring(ancUrl.length()),
                    req.getContextPath() );
            }
        }
        return null;
    }

    public static final class RunUrl {
        private final String contextPath;
        private final String basePortion;
        private final String rest;
        private final Run run;

        public RunUrl(Run run, String basePortion, String rest, String contextPath) {
            this.run = run;
            this.basePortion = basePortion;
            this.rest = rest;
            this.contextPath = contextPath;
        }

        public String getBaseUrl() {
            return basePortion;
        }

        /**
         * Returns the same page in the next build.
         */
        public String getNextBuildUrl() {
            return getUrl(run.getNextBuild());
        }

        /**
         * Returns the same page in the previous build.
         */
        public String getPreviousBuildUrl() {
            return getUrl(run.getPreviousBuild());
        }

        private String getUrl(Run n) {
            if(n ==null)
                return null;
            else {
                return basePortion+"/../"+n.getNumber()+rest;
            }
        }
    }

    public static Node.Mode[] getNodeModes() {
        return Node.Mode.values();
    }

    public static String getProjectListString(List<Project> projects) {
        return Items.toNameList(projects);
    }

    public static Object ifThenElse(boolean cond, Object thenValue, Object elseValue) {
        return cond ? thenValue : elseValue;
    }

    public static String appendIfNotNull(String text, String suffix, String nullText) {
        return text == null ? nullText : text + suffix;
    }

    public static Map getSystemProperties() {
        return new TreeMap<Object,Object>(System.getProperties());
    }

    public static Map getEnvVars() {
        return new TreeMap<String,String>(EnvVars.masterEnvVars);
    }

    public static boolean isWindows() {
        return File.pathSeparatorChar==';';
    }

    public static List<LogRecord> getLogRecords() {
        return Hudson.logRecords;
    }

    public static String printLogRecord(LogRecord r) {
        return formatter.format(r);
    }

    public static Cookie getCookie(HttpServletRequest req,String name) {
        Cookie[] cookies = req.getCookies();
        if(cookies!=null) {
            for (Cookie cookie : cookies) {
                if(cookie.getName().equals(name)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    public static String getCookie(HttpServletRequest req,String name, String defaultValue) {
        Cookie c = getCookie(req, name);
        if(c==null || c.getValue()==null) return defaultValue;
        return c.getValue();
    }

    /**
     * Gets the suffix to use for YUI JavaScript.
     */
    public static String getYuiSuffix() {
        return DEBUG_YUI ? "debug" : "min";
    }

    /**
     * Set to true if you need to use the debug version of YUI.
     */
    public static boolean DEBUG_YUI = false;

    /**
     * Creates a sub map by using the given range (both ends inclusive).
     */
    public static <V> SortedMap<Integer,V> filter(SortedMap<Integer,V> map, String from, String to) {
        if(from==null && to==null)      return map;
        if(to==null)
            return map.headMap(Integer.parseInt(from)-1);
        if(from==null)
            return map.tailMap(Integer.parseInt(to));

        return map.subMap(Integer.parseInt(to),Integer.parseInt(from)-1);
    }

    private static final SimpleFormatter formatter = new SimpleFormatter();

    /**
     * Used by <tt>layout.jelly</tt> to control the auto refresh behavior.
     *
     * @param noAutoRefresh
     *      On certain pages, like a page with forms, will have annoying interference
     *      with auto refresh. On those pages, disable auto-refresh.
     */
    public static void configureAutoRefresh(HttpServletRequest request, HttpServletResponse response, boolean noAutoRefresh) {
        if(noAutoRefresh)
            return;

        String param = request.getParameter("auto_refresh");
        boolean refresh = isAutoRefresh(request);
        if (param != null) {
            refresh = Boolean.parseBoolean(param);
            Cookie c = new Cookie("hudson_auto_refresh", Boolean.toString(refresh));
            // Need to set path or it will not stick from e.g. a project page to the dashboard.
            // Using request.getContextPath() might work but it seems simpler to just use the hudson_ prefix
            // to avoid conflicts with any other web apps that might be on the same machine.
            c.setPath("/");
            response.addCookie(c);
        }
        if (refresh) {
            response.addHeader("Refresh", "10");
        }
    }

    public static boolean isAutoRefresh(HttpServletRequest request) {
        String param = request.getParameter("auto_refresh");
        if (param != null) {
            return Boolean.parseBoolean(param);
        }
        Cookie[] cookies = request.getCookies();
        if(cookies==null)
            return false; // when API design messes it up, we all suffer

        for (Cookie c : cookies) {
            if (c.getName().equals("hudson_auto_refresh")) {
                return Boolean.parseBoolean(c.getValue());
            }
        }
        return false;
    }

    /**
     * Finds the given object in the ancestor list and returns its URL.
     * This is used to determine the "current" URL assigned to the given object,
     * so that one can compute relative URLs from it.
     */
    public static String getNearestAncestorUrl(StaplerRequest req,Object it) {
        List list = req.getAncestors();
        for( int i=list.size()-1; i>=0; i-- ) {
            Ancestor anc = (Ancestor) list.get(i);
            if(anc.getObject()==it)
                return anc.getUrl();
        }
        return null;
    }

    public static String appendSpaceIfNotNull(String n) {
        if(n==null) return null;
        else        return n+' ';
    }

    public static String getWin32ErrorMessage(IOException e) {
        return Util.getWin32ErrorMessage(e);
    }

    public static boolean isMultiline(String s) {
        if(s==null)     return false;
        return s.indexOf('\r')>=0 || s.indexOf('\n')>=0;
    }

    public static String encode(String s) {
        return Util.encode(s);
    }

    public static String escape(String s) {
        return Util.escape(s);
    }

    public static String xmlEscape(String s) {
        return Util.xmlEscape(s);
    }

    public static void adminCheck(StaplerRequest req, StaplerResponse rsp, Object required) throws IOException, ServletException {
        if(required!=null && !Hudson.adminCheck(req,rsp)) {
            // check failed
            throw new ServletException("Unauthorized access");
        }
    }

    /**
     * Infers the hudson installation URL from the given request.
     */
    public static String inferHudsonURL(StaplerRequest req) {
        StringBuilder buf = new StringBuilder();
        buf.append(req.getScheme()).append("://");
        buf.append(req.getServerName());
        if(req.getLocalPort()!=80)
            buf.append(':').append(req.getLocalPort());
        buf.append('/').append(req.getContextPath());
        return buf.toString();
    }

    public static List<JobPropertyDescriptor> getJobPropertyDescriptors(Class<? extends Job> clazz) {
        return JobPropertyDescriptor.getPropertyDescriptors(clazz);
    }

    /**
     * Computes the path to the icon of the given action
     * from the context path.
     */
    public static String getIconFilePath(Action a) {
        String name = a.getIconFileName();
        if(name.startsWith("/"))
            return name.substring(1);
        else
            return "images/24x24/"+name;
    }

    /**
     * Works like JSTL build-in size(x) function,
     * but handle null gracefully.
     */
    public static int size2(Object o) throws Exception {
        if(o==null) return 0;
        return ASTSizeFunction.sizeOf(o);
    }

    public static ExecutedMojo.Cache createExecutedMojoCache() {
        return new ExecutedMojo.Cache();
    }

    /**
     * Computes the relative path from the current page to the given item.
     */
    public static String getRelativeLinkTo(Item p) {
        Map<Object,String> ancestors = new HashMap<Object,String>();
        View view=null;

        StaplerRequest request = Stapler.getCurrentRequest();
        for( Ancestor a : request.getAncestors() ) {
            ancestors.put(a.getObject(),a.getRelativePath());
            if(a.getObject() instanceof View)
                view = (View) a.getObject();
        }

        String path = ancestors.get(p);
        if(path!=null)  return path;

        Item i=p;
        String url = "";
        while(true) {
            ItemGroup ig = i.getParent();
            url = i.getShortUrl()+url;

            if(ig==Hudson.getInstance()) {
                assert i instanceof TopLevelItem;
                if(view!=null && view.contains((TopLevelItem)i)) {
                    // if p and the current page belongs to the same view, then return a relative path
                    return ancestors.get(view)+'/'+url;
                } else {
                    // otherwise return a path from the root Hudson
                    return request.getContextPath()+'/'+p.getUrl();
                }
            }

            path = ancestors.get(ig);
            if(path!=null)  return path+'/'+url;

            assert ig instanceof Item; // if not, ig must have been the Hudson instance
            i = (Item) ig;
        }
    }

    public static Map<Thread,StackTraceElement[]> dumpAllThreads() {
        return Thread.getAllStackTraces();
    }

    public static ThreadInfo[] getThreadInfos() {
        ThreadMXBean mbean = ManagementFactory.getThreadMXBean();
        return mbean.getThreadInfo(mbean.getAllThreadIds(),mbean.isObjectMonitorUsageSupported(),mbean.isSynchronizerUsageSupported());
    }

    // ThreadInfo.toString() truncates the stack trace by first 8, so needed my own version
    public static String dumpThreadInfo(ThreadInfo ti) {
        StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"" +
                                             " Id=" + ti.getThreadId() + " " +
                                             ti.getThreadState());
        if (ti.getLockName() != null) {
            sb.append(" on " + ti.getLockName());
        }
        if (ti.getLockOwnerName() != null) {
            sb.append(" owned by \"" + ti.getLockOwnerName() +
                      "\" Id=" + ti.getLockOwnerId());
        }
        if (ti.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (ti.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        StackTraceElement[] stackTrace = ti.getStackTrace();
        for (int i=0; i < stackTrace.length; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && ti.getLockInfo() != null) {
                Thread.State ts = ti.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + ti.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : ti.getLockedMonitors()) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
       }

       LockInfo[] locks = ti.getLockedSynchronizers();
       if (locks.length > 0) {
           sb.append("\n\tNumber of locked synchronizers = " + locks.length);
           sb.append('\n');
           for (LockInfo li : locks) {
               sb.append("\t- " + li);
               sb.append('\n');
           }
       }
       sb.append('\n');
       return sb.toString();
    }

    public static <T> Collection<T> emptyList() {
        return Collections.emptyList();
    }
}
