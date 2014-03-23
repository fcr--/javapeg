/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package uy.com.netlabs.javapeg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uy.com.netlabs.javapeg.util.FastSnocList;
import uy.com.netlabs.javapeg.util.Function2;

/**
 *
 * @author fran
 * @param <T> generic "tag" type, mainly used for bottom-up processing of grammars.
 */
public abstract class ReduceFunction<T> {
    public abstract List<T> reduce(String text, ParserResult.AstNode node, List<T> immutableProcessedTags);

    public static <T> ReduceFunction<T> identity() {
        return null; // yes, yes... the null ReduceFunction does nothing.
    }

    public static <T> ReduceFunction<T> append(final T... ts) {
        return new ReduceFunction<T>() {
            @Override
            public List<T> reduce(String text, ParserResult.AstNode node, List<T> immutableProcessedTags) {
                List<T> res = immutableProcessedTags;
                for (T t: ts) {
                    res = FastSnocList.snoc(res, t);
                }
                return res;
            }
        };
    }

    public static <T> ReduceFunction<T> replace(final T... ts) {
        return new ReduceFunction<T>() {
            @Override
            public List<T> reduce(String text, ParserResult.AstNode node, List<T> immutableProcessedTags) {
                return Arrays.asList(ts);
            }
        };
    }

    public static <T> ReduceFunction<T> foldl(final T t0, final Function2<T, T, T> f) {
        return new ReduceFunction<T>() {
            @Override
            public List<T> reduce(String text, ParserResult.AstNode node, List<T> immutableProcessedTags) {
                T tcur = t0;
                for (T t: immutableProcessedTags) {
                    tcur = f.apply(tcur, t);
                }
                List<T> res = new ArrayList<>();
                res.add(tcur);
                return res;
            }
        };
    }


}
