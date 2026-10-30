package ar.com.blockazo.auth;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Per-process, per-source fixed windows for the expensive public credential endpoints. */
@Component
public class AuthRateLimiter {
  private static final int MAX_BUCKETS=10_000;
  private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
  private long calls;

  public synchronized boolean allow(String endpoint,String source,int limit,Duration duration){
    long now=System.currentTimeMillis();
    if((++calls&127)==0) windows.entrySet().removeIf(entry->now-entry.getValue().startedAt>=entry.getValue().durationMillis);
    String key=endpoint+":"+source;
    Window window=windows.get(key);
    if(window==null){
      if(windows.size()>=MAX_BUCKETS){windows.entrySet().removeIf(entry->now-entry.getValue().startedAt>=entry.getValue().durationMillis);if(windows.size()>=MAX_BUCKETS)return false;}
      window=new Window(now,duration.toMillis());windows.put(key,window);
    } else if(now-window.startedAt>=window.durationMillis){window=new Window(now,duration.toMillis());windows.put(key,window);}
    return ++window.count<=limit;
  }
  private static final class Window {
    private final long startedAt,durationMillis; private int count;
    private Window(long startedAt,long durationMillis){this.startedAt=startedAt;this.durationMillis=durationMillis;}
  }
}
