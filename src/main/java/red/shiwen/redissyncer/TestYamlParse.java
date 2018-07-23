package red.shiwen.redissyncer;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Map;

public class TestYamlParse {
    public static void main(String[] args) throws FileNotFoundException {
        URL url = TestYamlParse.class.getClassLoader().getResource("conf.yaml");
        Yaml yaml = new Yaml();
//        Object obj = yaml.load(new FileInputStream(url.getFile()));

        Map<String,Object> yamlmap= (Map<String, Object>) yaml.loadAs(new FileInputStream(url.getFile()), Map.class);

        System.out.println(yamlmap.get("Configuration"));
//        System.out.println(url.toString());

    }
}
