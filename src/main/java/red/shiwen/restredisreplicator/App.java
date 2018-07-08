package red.shiwen.restredisreplicator;

import com.moilioncircle.redis.replicator.RedisReplicator;
import com.moilioncircle.redis.replicator.Replicator;
import com.moilioncircle.redis.replicator.cmd.Command;
import com.moilioncircle.redis.replicator.cmd.CommandListener;
import com.moilioncircle.redis.replicator.rdb.RdbListener;
import com.moilioncircle.redis.replicator.rdb.datatype.KeyValuePair;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException, URISyntaxException {

        System.out.println("Hello World!");
        final Replicator replicator = new RedisReplicator("redis://118.184.216.157:6379");

        replicator.addRdbListener(new RdbListener.Adaptor() {
            public void handle(Replicator replicator, KeyValuePair<?> kv) {
                System.out.println(kv);
            }
        });

        replicator.addCommandListener(new CommandListener() {
            public void handle(Replicator replicator, Command command) {
                System.out.println(command);
            }
        });

        replicator.open();
    }
}

