package com.example.service;

import com.example.model.Auteur;
import com.example.model.Livre;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

public class DataInitService {

    private final EntityManagerFactory emf;

    public DataInitService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public void initData() {
        EntityManager em = emf.createEntityManager();
        EntityTransaction transaction = em.getTransaction();
        try {
            transaction.begin();
            for (int i = 1; i <= 10; i++) {
                Auteur auteur = new Auteur("Auteur " + i);
                for (int j = 1; j <= 5; j++) {
                    auteur.ajouterLivre(new Livre("Livre " + j + " de l'auteur " + i));
                }
                em.persist(auteur);
            }
            transaction.commit();
            System.out.println("Données initialisées : 10 auteurs et 50 livres.");
        } catch (RuntimeException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }
}
