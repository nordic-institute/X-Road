package ee.ria.xroad.common.conf.serverconf.dao;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;

class AbstractDAOImpl<T> {

    @SuppressWarnings("unchecked")
    public List<T> findMany(Query query) {
        return query.list();
    }

    @SuppressWarnings("unchecked")
    public T findOne(Query query) {
        return (T) query.uniqueResult();
    }

    @SuppressWarnings("unchecked")
    public T findOne(Criteria criteria) {
        return (T) criteria.uniqueResult();
    }

    @SuppressWarnings("unchecked")
    public T findById(Session session, Class<T> clazz, Long id)
            throws Exception {
        return (T) session.get(clazz, id);
    }

    @SuppressWarnings("unchecked")
    public List<T> findAll(Session session, Class<T> clazz) throws Exception {
        Query query = session.createQuery("from " + clazz.getName());
        return query.list();
    }

    static String nullOrName(Object obj, String name) {
        if (obj == null) {
            return "is null";
        } else {
            return " = :" + name;
        }
    }

    static void setString(Query q, String name, String value) {
        if (value != null) {
            q.setString(name, value);
        }
    }
}
