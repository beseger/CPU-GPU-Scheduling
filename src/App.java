import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import logger.InstanceDeserializer;
import util.Instance;

public class App {
    public static void main(String[] args) throws Exception {
        
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Instance.class, new InstanceDeserializer());
        mapper.registerModule(module);

        Instance I = mapper.readValue(Paths.get("TestInstance.json").toFile(), Instance.class);

        // Instance I = new Instance();

        // I.generateRandomInstance(8, 10, 2, 5);
        
        System.out.println(I);
        Algo.findTwoShelves(I, 100);
    }
}