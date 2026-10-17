package com.example.service;

import com.example.model.Auteur;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import javax.persistence.EntityGraph;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Locale;

public class PerformanceTestService {

    private final EntityManagerFactory emf;
    private final SessionFactory sessionFactory;
    private final Statistics statistics;

    public PerformanceTestService(EntityManagerFactory emf) {
        this.emf = emf;
        this.sessionFactory = emf.unwrap(SessionFactory.class);
        this.statistics = sessionFactory.getStatistics();
    }

    public void testPerformanceSansCache() {
        long requetes = mesurer("Sans cache : problème N+1",
                "SELECT a FROM Auteur a ORDER BY a.id", false, false);
        verifierRequetes(requetes, 11);
        System.out.println("1 requête pour les auteurs + 10 requêtes pour leurs livres.");
    }

    public void testPerformanceAvecJoinFetch() {
        long requetes = mesurer("Avec JOIN FETCH",
                "SELECT DISTINCT a FROM Auteur a JOIN FETCH a.livres ORDER BY a.id", false, false);
        verifierRequetes(requetes, 1);
        System.out.println("Les auteurs et leurs livres sont chargés en une seule requête.");
    }

    public void testPerformanceAvecEntityGraph() {
        long requetes = mesurer("Avec Entity Graph",
                "SELECT a FROM Auteur a ORDER BY a.id", true, false);
        verifierRequetes(requetes, 1);
        System.out.println("Le graphe charge les livres sans ajouter JOIN FETCH au JPQL.");
    }

    public void testPerformanceAvecCache() {
        EntityManager em = emf.createEntityManager();
        Long auteurId;
        try {
            auteurId = em.createQuery("SELECT a.id FROM Auteur a ORDER BY a.id", Long.class)
                    .setMaxResults(1).getSingleResult();
        } finally {
            em.close();
        }

        // Vider le cache pour que la première lecture accède réellement à la base.
        sessionFactory.getCache().evictAllRegions();
        verifierRequetes(mesurerAuteurParId("Cache L2 : première lecture par ID", auteurId), 1);
        verifierRequetes(mesurerAuteurParId("Cache L2 : deuxième lecture par ID", auteurId), 0);
        if (statistics.getSecondLevelCacheHitCount() == 0) {
            throw new IllegalStateException("La deuxième lecture doit utiliser le cache de second niveau.");
        }
        System.out.println("Le même auteur est relu dans un autre EntityManager depuis le cache L2.");

        sessionFactory.getCache().evictAllRegions();
        verifierRequetes(mesurer("Cache de requêtes : première lecture des auteurs et livres",
                "SELECT a FROM Auteur a ORDER BY a.id", false, true), 11);
        verifierRequetes(mesurer("Cache de requêtes : deuxième lecture des auteurs et livres",
                "SELECT a FROM Auteur a ORDER BY a.id", false, true), 0);
        if (statistics.getQueryCacheHitCount() == 0 || statistics.getSecondLevelCacheHitCount() == 0) {
            throw new IllegalStateException("Les caches de requêtes, d'entités et de collections doivent être utilisés.");
        }
        System.out.println("Les 10 auteurs et leurs 50 livres sont relus sans requête SQL.");
    }

    private long mesurer(String titre, String jpql, boolean avecGraph, boolean cacheable) {
        System.out.println("\n=== " + titre + " ===");
        // Un nouvel EntityManager évite de réutiliser le cache de premier niveau.
        EntityManager em = emf.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        statistics.clear();
        long debut = System.nanoTime();
        try {
            transaction.begin();
            TypedQuery<Auteur> query = em.createQuery(jpql, Auteur.class);
            if (avecGraph) {
                EntityGraph<?> graph = em.getEntityGraph("Auteur.livres");
                query.setHint("javax.persistence.fetchgraph", graph);
            }
            if (cacheable) {
                query.setHint("org.hibernate.cacheable", true);
            }

            List<Auteur> auteurs = query.getResultList();
            int nombreLivres = 0;
            for (Auteur auteur : auteurs) {
                nombreLivres += auteur.getLivres().size();
            }
            transaction.commit();
            double duree = (System.nanoTime() - debut) / 1_000_000.0;
            System.out.println("Auteurs : " + auteurs.size() + " | Livres : " + nombreLivres);
            afficherStatistiques(duree);
            if (auteurs.size() != 10 || nombreLivres != 50) {
                throw new IllegalStateException("Chaque scénario doit retrouver 10 auteurs et 50 livres.");
            }
            return statistics.getPrepareStatementCount();
        } catch (RuntimeException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    private long mesurerAuteurParId(String titre, Long id) {
        System.out.println("\n=== " + titre + " ===");
        EntityManager em = emf.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        statistics.clear();
        long debut = System.nanoTime();
        try {
            transaction.begin();
            Auteur auteur = em.find(Auteur.class, id);
            if (auteur == null) {
                throw new IllegalStateException("L'auteur doit exister.");
            }
            transaction.commit();
            double duree = (System.nanoTime() - debut) / 1_000_000.0;
            System.out.println("Auteur : " + auteur.getNom());
            afficherStatistiques(duree);
            return statistics.getPrepareStatementCount();
        } catch (RuntimeException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    private void afficherStatistiques(double duree) {
        System.out.printf(Locale.FRANCE, "Durée : %.3f ms%n", duree);
        System.out.println("Requêtes SQL : " + statistics.getPrepareStatementCount());
        System.out.println("Cache L2 : hits = " + statistics.getSecondLevelCacheHitCount()
                + " | misses = " + statistics.getSecondLevelCacheMissCount()
                + " | puts = " + statistics.getSecondLevelCachePutCount());
        System.out.println("Cache de requêtes : hits = " + statistics.getQueryCacheHitCount()
                + " | misses = " + statistics.getQueryCacheMissCount()
                + " | puts = " + statistics.getQueryCachePutCount());
    }

    private void verifierRequetes(long nombre, long attendu) {
        if (nombre != attendu) {
            throw new IllegalStateException("Nombre de requêtes attendu : " + attendu + ", obtenu : " + nombre);
        }
    }
}
