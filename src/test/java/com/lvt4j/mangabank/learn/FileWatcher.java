package com.lvt4j.mangabank.learn;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class FileWatcher{

    static Map<WatchKey, Path> keys = new HashMap<>();
    
    public static void main(String[] args) throws IOException{
        try{
            
            // 创建 WatchService
            WatchService watchService = FileSystems.getDefault().newWatchService();
            
            // 指定要监视的目录
            Path directory = Paths.get("E:\\Download\\kavita");
            registerAll(directory, watchService);
            
            
            
            // 监听文件系统事件
            while (true) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException ex) {
                    return;
                }
                
                Path path = keys.get(key);
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    
                    Path child = path.resolve(ev.context());
                    
                    System.out.println(kind + ": " + child);
                    
                    // 如果是ENTRY_CREATE事件，并且是目录，则注册新目录
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                            registerAll(child, watchService);
                        }
                    }
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    keys.remove(key);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
            // TODO: handle exception
        }
        
    }
    
    private static void registerAll(Path start, WatchService watchService) throws IOException {
        Files.walk(start)
             .filter(Files::isDirectory)
             .forEach(dir -> {
                 try {
                     WatchKey key = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                     keys.put(key, dir);
                 } catch (IOException e) {
                     e.printStackTrace();
                 }
             });
    }
    
}
