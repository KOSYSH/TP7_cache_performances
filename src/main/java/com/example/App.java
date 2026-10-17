package com.example;

import com.example.service.DataInitService;
import com.example.service.PerformanceTestService;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public class App {

    public static void main(String[] args) {
        // Comparaison sans cache sur les mêmes données, dans une base séparée.
        Map<String, Object> sansCache = new HashMap<>();
        sansCache.put("javax.persistence.jdbc.url", "jdbc:h2:mem:sans_cache;DB_CLOSE_DELAY=-1");
        sansCache.put("hibernate.cache.use_second_level_cache", false);
        sansCache.put("hibernate.cache.use_query_cache", false);

        System.out.println("\n=== Première partie : N+1, JOIN FETCH et Entity Graph ===");
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("hibernate-cache-pu", sansCache);
        try {
            new DataInitService(emf).initData();
            PerformanceTestService service = new PerformanceTestService(emf);
            service.testPerformanceSansCache();
            service.testPerformanceAvecJoinFetch();
            service.testPerformanceAvecEntityGraph();
        } finally {
            emf.close();
        }

        System.out.println("\n=== Deuxième partie : cache de second niveau ===");
        emf = Persistence.createEntityManagerFactory("hibernate-cache-pu");
        try {
            new DataInitService(emf).initData();
            new PerformanceTestService(emf).testPerformanceAvecCache();
        } finally {
            emf.close();
        }

        System.out.println("\nTous les scénarios du TP ont été exécutés avec succès.");
        System.out.println("Les durées varient : comparez surtout les requêtes SQL et les hits du cache.");
    }
}
