package com.pearson.fixie;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.AbstractConstruct;
import org.yaml.snakeyaml.extensions.compactnotation.CompactConstructor;
import org.yaml.snakeyaml.extensions.compactnotation.CompactData;
import org.yaml.snakeyaml.extensions.compactnotation.PackageCompactConstructor;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import javax.persistence.EntityManager;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class EntityConstructor extends CompactConstructor {
    Map<String, Object> entityCache = Maps.newLinkedHashMap();
    Multimap<Class<?>, EntityPostProcessor<? super Object>> postProcessors = HashMultimap.create();
    private List<Object> allEntities = Lists.newArrayList();
    private Queue<Object> processQueue = Lists.newLinkedList();
    private String packageName;

    public EntityConstructor() {
        //this.yamlConstructors.put(new Tag("!import"), new ConstructImport());
        this.yamlConstructors.put(new Tag("!package"), new ConstructPackage());
        this.packageName = "";
    }

    /*
    class ConstructImport extends AbstractConstruct {
        @Override public Object construct(Node node) {
            String location = ((ScalarNode) node).getValue();

            new Yaml(EntityConstructor.this).load(getClass().getResourceAsStream(location));
            return null;
        }
    }
    */

    class ConstructPackage extends AbstractConstruct {
        @Override public Object construct(Node node) {
            String packageName = ((ScalarNode) node).getValue();
            EntityConstructor.this.packageName = packageName;
            return null;
        }
    }


    @Override
    protected Class<?> getClassForName(String name) throws ClassNotFoundException {
        if(!Strings.isNullOrEmpty(packageName)) {
            return super.getClassForName(packageName + "." + name);
        } else {
            return super.getClassForName(name);
        }
    }
    
    @Override
    protected Object createInstance(ScalarNode node, CompactData data) throws Exception {
        List<String> arguments = Lists.newArrayList();
        if(!entityCache.containsKey(node.getValue())) {
            data.getArguments().clear();
            Object entity = super.createInstance(node, data);
            entityCache.put(node.getValue(), entity);
        }
        return entityCache.get(node.getValue());
    }

    public void loadEntities(InputStream... files) {
        Yaml yaml = new Yaml(this);
        for(InputStream file : files) {
            yaml.load(file);
        }
    }

    public void persistEntities(EntityManager entityManager) {
        Queue<Object> processQueue = new LinkedList<Object>(entityCache.values());
        allEntities.addAll(entityCache.values());
        while(!processQueue.isEmpty()) {
            Object entity = processQueue.remove();
            for(Map.Entry<Class<?>, EntityPostProcessor<? super Object>> entry : postProcessors.entries()) {
                if(entity.getClass().isAssignableFrom(entry.getKey())) {
                    EntityPostProcessor<? super Object> postProcessor = entry.getValue();
                    postProcessor.processQueue = processQueue;
                    postProcessor.allEntities = allEntities;
                    postProcessor.process(entity);
                }
            }
            entityManager.persist(entity);
        }
    }

    public <T> void addPostProcessor(EntityPostProcessor<T> postProcessor) {
        postProcessors.put(postProcessor.getType(), (EntityPostProcessor<? super Object>) postProcessor);
    }
}
