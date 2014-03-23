/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uy.com.netlabs.javapeg.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 *
 * @author fran
 */
/**
 * A simple implementation of the List providing O(1) performance for the snoc method. And wrapping an ArrayList for
 * almost all the remaining operations (for compatibility and performance reasons).
 *
 * @param <V>
 */
public class FastSnocList<V> implements List<V> {

    private List<V> flattenedList = null;
    private int len;
    private List<V> init;
    private V lastElement;

    private FastSnocList(int len, List<V> init, V newElement) {
        this.len = len;
        this.init = init;
        this.lastElement = newElement;
    }

    @Override
    public int size() {
        if (flattenedList != null) {
            return flattenedList.size();
        }
        return len;
    }

    @Override
    public boolean isEmpty() {
        if (flattenedList != null) {
            return flattenedList.isEmpty();
        }
        return len != 0;
    }

    private void flattenList() {
        if (flattenedList != null) {
            return;
        }
        flattenedList = new ArrayList(len);
        flattenedList.set(len - 1, lastElement);
        List<V> initList = init;
        for (int i = len - 2; i >= 0; i--) {
            if (!(initList instanceof FastSnocList)) {
                int j = 0;
                for (V v: initList) {
                    flattenedList.set(j++, v);
                }
                break;
            }
            FastSnocList<V> castedInitList = (FastSnocList<V>) initList;
            flattenedList.set(i, castedInitList.lastElement);
            initList = castedInitList.init;
        }
        // delete references allowing garbage collection:
        init = null;
        lastElement = null;
    }

    @Override
    public boolean contains(Object o) {
        flattenList();
        return flattenedList.contains(o);
    }

    @Override
    public Iterator<V> iterator() {
        flattenList();
        return flattenedList.iterator();
    }

    @Override
    public Object[] toArray() {
        flattenList();
        return flattenedList.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        flattenList();
        return flattenedList.toArray(a);
    }

    @Override
    public boolean add(V e) {
        if (flattenedList != null) {
            return flattenedList.add(e);
        }
        FastSnocList<V> old = new FastSnocList<>(len, init, lastElement);
        init = old;
        len++;
        lastElement = e;
        return true;
    }

    @Override
    public boolean remove(Object o) {
        flattenList();
        return flattenedList.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        flattenList();
        return flattenedList.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        if (flattenedList != null) {
            return flattenedList.addAll(c);
        }
        for (V v: c) {
            add(v);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends V> c) {
        if (flattenedList != null) {
            return flattenedList.addAll(c);
        } else if (index == len) {
            return addAll(c);
        } else {
            flattenList();
            return flattenedList.addAll(c);
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        flattenList();
        return flattenedList.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        flattenList();
        return flattenedList.removeAll(c);
    }

    @Override
    public void clear() {
        flattenedList = new ArrayList<V>();
    }

    @Override
    public V get(int index) {
        if (flattenedList != null) {
            return flattenedList.get(index);
        }
        if (index == len - 1) {
            return lastElement;
        }
        flattenList();
        return flattenedList.get(index);
    }

    @Override
    public V set(int index, V element) {
        if (flattenedList != null) {
            return flattenedList.set(index, element);
        }
        if (index == len - 1) {
            V old = lastElement;
            lastElement = element;
            return old;
        }
        flattenList();
        return flattenedList.set(index, element);
    }

    @Override
    public void add(int index, V element) {
        if (flattenedList != null) {
            flattenedList.add(index, element);
        } else if (index == len - 1) {
            add(element);
        } else {
            flattenList();
            add(index, element);
        }
    }

    @Override
    public V remove(int index) {
        flattenList();
        return flattenedList.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        flattenList();
        return flattenedList.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        flattenList();
        return flattenedList.lastIndexOf(o);
    }

    @Override
    public ListIterator<V> listIterator() {
        flattenList();
        return flattenedList.listIterator();
    }

    @Override
    public ListIterator<V> listIterator(int index) {
        flattenList();
        return flattenedList.listIterator(index);
    }

    @Override
    public List<V> subList(int fromIndex, int toIndex) {
        flattenList();
        return flattenedList.subList(fromIndex, toIndex);
    }

    public FastSnocList<V> snoc(V last) {
        return FastSnocList.snoc(this, last);
    }

    public static <V> FastSnocList<V> snoc(List<V> init, V last) {
        return new FastSnocList<>(init.size() + 1, init, last);
    }
}
