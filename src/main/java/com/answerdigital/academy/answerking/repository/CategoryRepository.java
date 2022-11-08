package com.answerdigital.academy.answerking.repository;

import com.answerdigital.academy.answerking.model.Category;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface CategoryRepository extends PagingAndSortingRepository<Category, Long> {
    boolean existsByName(String name);

    boolean existsByNameAndIdIsNot(String name, Long id);

    Set<Category> findAll();
}