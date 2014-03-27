/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uy.com.netlabs.javapeg.util;

/**
 *
 * @author fran
 * @param <A> first function parameter
 * @param <R> result
 */
public interface Function1<A, R> {

    public R apply(A a);
}
