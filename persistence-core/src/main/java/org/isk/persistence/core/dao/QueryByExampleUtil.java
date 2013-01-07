package org.isk.persistence.core.dao;

import static com.google.common.collect.Lists.newArrayList;
import static javax.persistence.metamodel.Attribute.PersistentAttributeType.EMBEDDED;
import static javax.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_ONE;
import static javax.persistence.metamodel.Attribute.PersistentAttributeType.ONE_TO_ONE;
import static org.apache.commons.lang.StringUtils.isNotEmpty;

import java.lang.reflect.Method;
import java.util.List;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.ManagedType;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import org.springframework.util.ReflectionUtils;

import com.google.common.base.Throwables;

/**
 * Helper to create find by example query.
 */
@Named
@Singleton
public class QueryByExampleUtil {

    @PersistenceContext
    private EntityManager em;

    public <T> Predicate byExampleOnEntity(Root<T> rootPath, final T entityValue, SearchParameters sp, CriteriaBuilder builder) {
        if (entityValue == null) {
            return null;
        }

        Class<T> type = rootPath.getModel().getBindableJavaType();
        ManagedType<T> mt = em.getMetamodel().entity(type);

        List<Predicate> predicates = newArrayList();
        byExample(mt, rootPath, entityValue, sp, builder, predicates);
        byExampleOnManyToMany(mt, rootPath, entityValue, sp, builder, predicates);
        return JpaUtil.andPredicate(predicates, builder);
    }

    public <E> Predicate byExampleOnEmbeddable(Path<E> embeddablePath, final E embeddableValue, SearchParameters sp, CriteriaBuilder builder) {
        if (embeddableValue == null) {
            return null;
        }

        Class<E> type = embeddablePath.getModel().getBindableJavaType();
        ManagedType<E> mt = em.getMetamodel().embeddable(type); // note: calling .managedType() does not work

        List<Predicate> predicates = newArrayList();
        byExample(mt, embeddablePath, embeddableValue, sp, builder, predicates);
        return JpaUtil.andPredicate(predicates, builder);
    }

    private <T> void byExample(ManagedType<T> mt, Path<T> mtPath, final T mtValue, SearchParameters sp, CriteriaBuilder builder, List<Predicate> predicates) {
        for (SingularAttribute<? super T, ?> attr : mt.getSingularAttributes()) {
            if (attr.getPersistentAttributeType() == MANY_TO_ONE //
                    || attr.getPersistentAttributeType() == ONE_TO_ONE //
                    || attr.getPersistentAttributeType() == EMBEDDED) {
                continue;
            }

            Object attrValue = getValue(mtValue, attr);
            if (attrValue != null) {
                if (attr.getJavaType() == String.class) {
                    if (isNotEmpty((String) attrValue)) {
                        predicates.add(JpaUtil.stringPredicate(mtPath.get(stringAttribute(mt, attr)), attrValue, sp, builder));
                    }
                } else {
                    predicates.add(builder.equal(mtPath.get(attribute(mt, attr)), attrValue));
                }
            }
        }
    }

    /**
     * Construct a join predicate on collection (eg many to many, List)
     */
    private <T> void byExampleOnManyToMany(ManagedType<T> mt, Root<T> mtPath, final T mtValue, SearchParameters sp, CriteriaBuilder builder,
            List<Predicate> predicates) {
        for (PluralAttribute<T, ?, ?> pa : mt.getDeclaredPluralAttributes()) {
            if (pa.getCollectionType() == PluralAttribute.CollectionType.LIST) {
                List<?> value = (List<?>) getValue(mtValue, mt.getAttribute(pa.getName()));

                if (value != null && !value.isEmpty()) {
                    ListJoin<T, ?> join = mtPath.join(mt.getDeclaredList(pa.getName()));
                    Predicate p = join.in(value);
                    predicates.add(p);
                }
            }
        }
    }

    private <T> Object getValue(T example, Attribute<? super T, ?> attr) {
        try {
            return ReflectionUtils.invokeMethod((Method) attr.getJavaMember(), example);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    private <T, A> SingularAttribute<? super T, A> attribute(ManagedType<? super T> mt, Attribute<? super T, A> attr) {
        return mt.getSingularAttribute(attr.getName(), attr.getJavaType());
    }

    private <T> SingularAttribute<? super T, String> stringAttribute(ManagedType<? super T> mt, Attribute<? super T, ?> attr) {
        return mt.getSingularAttribute(attr.getName(), String.class);
    }
}