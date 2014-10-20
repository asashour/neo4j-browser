/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.csv.reader;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static java.lang.reflect.Modifier.isStatic;

/**
 * Common implementations of {@link Extractor}. Since array values can have a delimiter of user choice this isn't
 * an enum, but a regular class with a constructor where that delimiter can be specified.
 */
public class Extractors
{
    private final Map<String, Extractor<?>> instances = new HashMap<>();
    private final Extractor<String[]> stringArray;
    private final Extractor<boolean[]> booleanArray;
    private final Extractor<byte[]> byteArray;
    private final Extractor<short[]> shortArray;
    private final Extractor<int[]> intArray;
    private final Extractor<long[]> longArray;
    private final Extractor<float[]> floatArray;
    private final Extractor<double[]> doubleArray;

    /**
     * Why do we have a public constructor here and why isn't this class an enum?
     * It's because the array extractors can be configured with an array delimiter,
     * something that would be impossible otherwise. There's an equivalent {@link #valueOf(String)}
     * method to keep the feel of an enum.
     */
    public Extractors( char arrayDelimiter )
    {
        try
        {
            for ( Field field : getClass().getDeclaredFields() )
            {
                if ( isStatic( field.getModifiers() ) )
                {
                    Object value = field.get( null );
                    if ( value instanceof Extractor )
                    {
                        instances.put( field.getName(), (Extractor<?>) value );
                    }
                }
            }

            add( stringArray = new StringArray( arrayDelimiter ) );
            add( booleanArray = new BooleanArray( arrayDelimiter ) );
            add( byteArray = new ByteArray( arrayDelimiter ) );
            add( shortArray = new ShortArray( arrayDelimiter ) );
            add( intArray = new IntArray( arrayDelimiter ) );
            add( longArray = new LongArray( arrayDelimiter ) );
            add( floatArray = new FloatArray( arrayDelimiter ) );
            add( doubleArray = new DoubleArray( arrayDelimiter ) );
        }
        catch ( IllegalAccessException e )
        {
            throw new Error( "Bug in reflection code gathering all extractors" );
        }
    }

    private void add( Extractor<?> extractor )
    {
        instances.put( extractor.toString().toUpperCase(), extractor );
    }

    public Extractor<?> valueOf( String name )
    {
        Extractor<?> instance = instances.get( name.toUpperCase() );
        if ( instance == null )
        {
            throw new IllegalArgumentException( "'" + name + "'" );
        }
        return instance;
    }

    public Extractor<String[]> stringArray()
    {
        return stringArray;
    }

    public Extractor<boolean[]> booleanArray()
    {
        return booleanArray;
    }

    public Extractor<byte[]> byteArray()
    {
        return byteArray;
    }

    public Extractor<short[]> shortArray()
    {
        return shortArray;
    }

    public Extractor<int[]> intArray()
    {
        return intArray;
    }

    public Extractor<long[]> longArray()
    {
        return longArray;
    }

    public Extractor<float[]> floatArray()
    {
        return floatArray;
    }

    public Extractor<double[]> doubleArray()
    {
        return doubleArray;
    }

    public static Extractor<String> STRING = new Extractor<String>()
    {
        @Override
        public String extract( char[] data, int offset, int length )
        {
            return new String( data, offset, length );
        }
    };

    public static Extractor<Long> LONG = new Extractor<Long>()
    {
        @Override
        public Long extract( char[] data, int offset, int length )
        {
            return extractLong( data, offset, length );
        }
    };

    public static Extractor<Integer> INT = new Extractor<Integer>()
    {
        @Override
        public Integer extract( char[] data, int offset, int length )
        {
            return safeCastLongToInt( extractLong( data, offset, length ) );
        }
    };

    public static Extractor<Short> SHORT = new Extractor<Short>()
    {
        @Override
        public Short extract( char[] data, int offset, int length )
        {
            return safeCastLongToShort( extractLong( data, offset, length ) );
        }
    };

    public static Extractor<Byte> BYTE = new Extractor<Byte>()
    {
        @Override
        public Byte extract( char[] data, int offset, int length )
        {
            return safeCastLongToByte( extractLong( data, offset, length ) );
        }
    };

    public static Extractor<Boolean> BOOLEAN = new Extractor<Boolean>()
    {
        private final char[] match;
        {
            match = new char[Boolean.TRUE.toString().length()];
            Boolean.TRUE.toString().getChars( 0, match.length, match, 0 );
        }

        @Override
        public Boolean extract( char[] data, int offset, int length )
        {
            return extractBoolean( data, offset, length );
        }
    };

    public static Extractor<Character> CHAR = new Extractor<Character>()
    {
        @Override
        public Character extract( char[] data, int offset, int length )
        {
            if ( length != 1 )
            {
                throw new IllegalStateException( "Was told to extract a character, but length:" + length );
            }

            return Character.valueOf( data[offset] );
        }
    };

    public static Extractor<Float> FLOAT = new Extractor<Float>()
    {
        @Override
        public Float extract( char[] data, int offset, int length )
        {
            // TODO Figure out a way to do this conversion without round tripping to String
            return Float.valueOf( String.valueOf( data, offset, length ) );
        }
    };

    public static Extractor<Double> DOUBLE = new Extractor<Double>()
    {
        @Override
        public Double extract( char[] data, int offset, int length )
        {
            // TODO Figure out a way to do this conversion without round tripping to String
            return Double.valueOf( String.valueOf( data, offset, length ) );
        }
    };

    private static abstract class Array<T> implements Extractor<T>
    {
        protected final char arrayDelimiter;
        private final Class<?> componentType;

        Array( char arrayDelimiter, Class<?> componentType )
        {
            this.arrayDelimiter = arrayDelimiter;
            this.componentType = componentType;
        }

        protected int charsToNextDelimiter( char[] data, int offset, int length )
        {
            for ( int i = 0; i < length; i++ )
            {
                if ( data[offset+i] == arrayDelimiter )
                {
                    return i;
                }
            }
            return length;
        }

        protected int numberOfValues( char[] data, int offset, int length )
        {
            int count = length > 0 ? 1 : 0;
            for ( int i = 0; i < length; i++ )
            {
                if ( data[offset+i] == arrayDelimiter )
                {
                    count++;
                }
            }
            return count;
        }

        @Override
        public int hashCode()
        {
            return getClass().hashCode();
        }

        @Override
        public boolean equals( Object obj )
        {
            return getClass().equals( obj.getClass() );
        }

        @Override
        public String toString()
        {
            return componentType.getSimpleName() + "[]";
        }
    }

    private static class StringArray extends Array<String[]>
    {
        private static final String[] EMPTY = new String[0];

        StringArray( char arrayDelimiter )
        {
            super( arrayDelimiter, String.class );
        }

        @Override
        public String[] extract( char[] data, int offset, int length )
        {
            int numberOfValues = numberOfValues( data, offset, length );
            String[] array = numberOfValues > 0 ? new String[numberOfValues] : EMPTY;
            for ( int arrayIndex = 0, charIndex = 0; arrayIndex < numberOfValues; arrayIndex++, charIndex++ )
            {
                int numberOfChars = charsToNextDelimiter( data, offset+charIndex, length-charIndex );
                array[arrayIndex] = new String( data, offset+charIndex, numberOfChars );
                charIndex += numberOfChars;
            }
            return array;
        }
    }

    private static class ByteArray extends Array<byte[]>
    {
        private static final byte[] EMPTY = new byte[0];

        ByteArray( char arrayDelimiter )
        {
            super( arrayDelimiter, Byte.TYPE );
        }

        @Override
        public byte[] extract( char[] data, int offset, int length )
        {
            int numberOfValues = numberOfValues( data, offset, length );
            byte[] array = numberOfValues > 0 ? new byte[numberOfValues] : EMPTY;
            for ( int arrayIndex = 0, charIndex = 0; arrayIndex < numberOfValues; arrayIndex++, charIndex++ )
            {
                int numberOfChars = charsToNextDelimiter( data, offset+charIndex, length-charIndex );
                array[arrayIndex] = safeCastLongToByte( extractLong( data, offset+charIndex, numberOfChars ) );
                charIndex += numberOfChars;
            }
            return array;
        }
    }

    private static class ShortArray extends Array<short[]>
    {
        private static final short[] EMPTY = new short[0];

        ShortArray( char arrayDelimiter )
        {
            super( arrayDelimiter, Short.TYPE );
        }

        @Override
        public short[] extract( char[] data, int offset, int length )
        {
            int numberOfValues = numberOfValues( data, offset, length );
            short[] array = numberOfValues > 0 ? new short[numberOfValues] : EMPTY;
            for ( int arrayIndex = 0, charIndex = 0; arrayIndex < numberOfValues; arrayIndex++, charIndex++ )
            {
                int numberOfChars = charsToNextDelimiter( data, offset+charIndex, length-charIndex );
                array[arrayIndex] = safeCastLongToShort( extractLong( data, offset+charIndex, numberOfChars ) );
                charIndex += numberOfChars;
            }
            return array;
        }
    }

    private static class IntArray extends Array<int[]>
    {
        private static final int[] EMPTY = new int[0];

        IntArray( char arrayDelimiter )
        {
            super( arrayDelimiter, Integer.TYPE );
        }

        @Override
        public int[] extract( char[] data, int offset, int length )
        {
            int numberOfValues = numberOfValues( data, offset, length );
            int[] array = numberOfValues > 0 ? new int[numberOfValues] : EMPTY;
            for ( int arrayIndex = 0, charIndex = 0; arrayIndex < numberOfValues; arrayIndex++, charIndex++ )
            {
                int numberOfChars = charsToNextDelimiter( data, offset+charIndex, length-charIndex );
                array[arrayIndex] = safeCastLongToInt( extractLong( data, offset+charIndex, numberOfChars ) );
                charIndex += numberOfChars;
            }
            return array;
        }
    }

    private static class LongArray extends Array<long[]>
    {
        private static final long[] EMPTY = new long[0];

        LongArray( char arrayDelimiter )
        {
            super( arrayDelimiter, Long.TYPE );
        }

        @Override
        public long[] extract( char[] data, int offset, int length )
        {
            int numberOfValues = numberOfValues( data, offset, length );
            long[] array = numberOfValues > 0 ? new long[numberOfValues] : EMPTY;
            for ( int arrayIndex = 0, charIndex = 0; arrayIndex < numberOfValues; arrayIndex++, charIndex++ )
            {
                int numberOfChars = charsToNextDelimiter( data, offset+charIndex, length-charIndex );
                array[arrayIndex] = extractLong( data, offset+charIndex, numberOfChars );
                charIndex += numberOfChars;
            }
            return array;
        }
    }

    private static class FloatArray extends Array<float[]>
    {
        private static final float[] EMPTY = new float[0];

        FloatArray( char arrayDelimiter )
        {
            super( arrayDelimiter, Float.TYPE );
        }

        @Override
        public float[] extract( char[] data, int offset, int length )
        {
            int numberOfValues = numberOfValues( data, offset, length );
            float[] array = numberOfValues > 0 ? new float[numberOfValues] : EMPTY;
            for ( int arrayIndex = 0, charIndex = 0; arrayIndex < numberOfValues; arrayIndex++, charIndex++ )
            {
                int numberOfChars = charsToNextDelimiter( data, offset+charIndex, length-charIndex );
                // TODO Figure out a way to do this conversion without round tripping to String
                array[arrayIndex] = Float.parseFloat( String.valueOf( data, offset+charIndex, numberOfChars ) );
                charIndex += numberOfChars;
            }
            return array;
        }
    }

    private static class DoubleArray extends Array<double[]>
    {
        private static final double[] EMPTY = new double[0];

        DoubleArray( char arrayDelimiter )
        {
            super( arrayDelimiter, Double.TYPE );
        }

        @Override
        public double[] extract( char[] data, int offset, int length )
        {
            int numberOfValues = numberOfValues( data, offset, length );
            double[] array = numberOfValues > 0 ? new double[numberOfValues] : EMPTY;
            for ( int arrayIndex = 0, charIndex = 0; arrayIndex < numberOfValues; arrayIndex++, charIndex++ )
            {
                int numberOfChars = charsToNextDelimiter( data, offset+charIndex, length-charIndex );
                // TODO Figure out a way to do this conversion without round tripping to String
                array[arrayIndex] = Double.parseDouble( String.valueOf( data, offset+charIndex, numberOfChars ) );
                charIndex += numberOfChars;
            }
            return array;
        }
    }

    private static class BooleanArray extends Array<boolean[]>
    {
        private static final boolean[] EMPTY = new boolean[0];

        BooleanArray( char arrayDelimiter )
        {
            super( arrayDelimiter, Boolean.TYPE );
        }

        @Override
        public boolean[] extract( char[] data, int offset, int length )
        {
            int numberOfValues = numberOfValues( data, offset, length );
            boolean[] array = numberOfValues > 0 ? new boolean[numberOfValues] : EMPTY;
            for ( int arrayIndex = 0, charIndex = 0; arrayIndex < numberOfValues; arrayIndex++, charIndex++ )
            {
                int numberOfChars = charsToNextDelimiter( data, offset+charIndex, length-charIndex );
                array[arrayIndex] = extractBoolean( data, offset+charIndex, numberOfChars );
                charIndex += numberOfChars;
            }
            return array;
        }
    }

    private static long extractLong( char[] data, int offset, int length )
    {
        if ( length == 0 )
        {
            throw new NumberFormatException( "For input string \"" + String.valueOf( data, offset, length ) + "\"" );
        }

        long result = 0;
        int i = 0;
        boolean negate = false;
        if ( data[offset] == '-' )
        {
            negate = true;
            i++;
        }
        for ( ; i < length; i++ )
        {
            result = result*10 + digit( data[offset+i] );
        }
        return negate ? -result : result;
    }

    private static int digit( char ch )
    {
        int digit = ch - '0';
        if ( (digit < 0) || (digit > 9) )
        {
            throw new NumberFormatException( "Invalid digit character " + digit + " '" + (char)digit +
                    "' where the original char was '" + ch + "'" );
        }
        return digit;
    }

    private static final char[] BOOLEAN_TRUE_CHARACTERS;
    static
    {
        BOOLEAN_TRUE_CHARACTERS = new char[Boolean.TRUE.toString().length()];
        Boolean.TRUE.toString().getChars( 0, BOOLEAN_TRUE_CHARACTERS.length, BOOLEAN_TRUE_CHARACTERS, 0 );
    }

    private static boolean extractBoolean( char[] data, int offset, int length )
    {
        if ( BOOLEAN_TRUE_CHARACTERS.length != length )
        {
            return false;
        }
        for ( int i = 0; i < length; i++ )
        {
            if ( data[offset+i] != BOOLEAN_TRUE_CHARACTERS[i] )
            {
                return false;
            }
        }
        return true;
    }

    private static int safeCastLongToInt( long value )
    {
        if ( value > Integer.MAX_VALUE )
        {
            throw new UnsupportedOperationException( "Not supported a.t.m" );
        }
        return (int) value;
    }

    private static short safeCastLongToShort( long value )
    {
        if ( value > Short.MAX_VALUE )
        {
            throw new UnsupportedOperationException( "Not supported a.t.m" );
        }
        return (short) value;
    }

    private static byte safeCastLongToByte( long value )
    {
        if ( value > Byte.MAX_VALUE )
        {
            throw new UnsupportedOperationException( "Not supported a.t.m" );
        }
        return (byte) value;
    }
}