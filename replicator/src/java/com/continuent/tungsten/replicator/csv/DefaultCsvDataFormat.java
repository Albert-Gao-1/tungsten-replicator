/**
 * VMware Continuent Tungsten Replicator
 * Copyright (C) 2015 VMware, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *      
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Initial developer(s): Robert Hodges
 * Contributor(s): 
 */

package com.continuent.tungsten.replicator.csv;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.sql.rowset.serial.SerialException;

import com.continuent.tungsten.replicator.ReplicatorException;
import com.continuent.tungsten.replicator.extractor.mysql.SerialBlob;

/**
 * Denotes a file that converts Java object instances to strings suitable for
 * most CSV file consumers.
 */
public class DefaultCsvDataFormat implements CsvDataFormat
{
    // Properties.
    protected TimeZone       timezone;
    protected TimeZone       basetimezone;

    // Formatting support.
    private static final ThreadLocal<SimpleDateFormat> dateFormatter = new ThreadLocal<SimpleDateFormat>()
    {
        @Override
        protected SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat();
        }
    };
    private static final ThreadLocal<SimpleDateFormat> datetimeFormatter = new ThreadLocal<SimpleDateFormat>()
    {
        @Override
        protected SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat();
        }
    };
    private static final ThreadLocal<SimpleDateFormat> timestampFormatter = new ThreadLocal<SimpleDateFormat>()
    {
        @Override
        protected SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat();
        }
    };
    private static final ThreadLocal<SimpleDateFormat> timeFormatter = new ThreadLocal<SimpleDateFormat>()
    {
        @Override
        protected SimpleDateFormat initialValue()
        {
            return new SimpleDateFormat();
        }
    };

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.csv.CsvDataFormat#setTimeZone(java.util.TimeZone)
     */
    public void setTimeZone(TimeZone timezone)
    {
        this.timezone = timezone;
        this.basetimezone = TimeZone.getTimeZone("UTC");
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.csv.CsvDataFormat#prepare()
     */
    public void prepare()
    {
        // Dates are formatted with the date only.
        dateFormatter.get().setTimeZone(timezone);
        dateFormatter.get().applyPattern("yyyy-MM-dd");

        datetimeFormatter.get().setTimeZone(basetimezone);
        datetimeFormatter.get().applyPattern("yyyy-MM-dd HH:mm:ss");

        timestampFormatter.get().setTimeZone(timezone);
        timestampFormatter.get().applyPattern("yyyy-MM-dd HH:mm:ss.SSS");

        timeFormatter.get().setTimeZone(timezone);
        timeFormatter.get().applyPattern("HH:mm:ss");
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.csv.CsvDataFormat#csvString(java.lang.Object,
     *      int, boolean)
     */
    public String csvString(Object value, int javaType, boolean isBlob)
            throws ReplicatorException
    {
        // For examples of calling conventions for this method see class
        // SimplBatchApplier.
        if (value == null)
        {
            return null;
        }
        else if (value instanceof Timestamp)
        {
            if (javaType == Types.TIME)
            {
                return timeFormatter.get().format((Timestamp) value);
            }
            else if (javaType == Types.DATE)
            {
                return datetimeFormatter.get().format((Timestamp) value);
            }
            else
            {
                return timestampFormatter.get().format((Timestamp) value);
            }
        }
        else if (value instanceof java.sql.Date)
        {
            return dateFormatter.get().format((java.sql.Date) value);
        }
        else if (value instanceof java.sql.Time)
        {
            return timeFormatter.get().format((java.sql.Time) value);
        }
        else if (javaType == Types.BLOB
                || (javaType == Types.NULL && value instanceof SerialBlob))
        { // ______^______
          // Blob in the incoming event masked as NULL,
          // though this happens with a non-NULL value!
          // Case targeted with this: MySQL.TEXT -> CSV
            SerialBlob blob = (SerialBlob) value;

            if (isBlob)
            {
                // If it's really a blob, convert to hex values.
                InputStream blobStream = null;
                try
                {
                    StringBuilder sb = new StringBuilder();
                    blobStream = blob.getBinaryStream();
                    int nextByte = -1;
                    while ((nextByte = blobStream.read()) > -1)
                    {
                        sb.append(String.format("%02x", nextByte));
                    }
                    return sb.toString();
                }
                catch (IOException e)
                {
                    throw new ReplicatorException(
                            "Exception while reading blob data", e);
                }
                catch (SerialException e)
                {
                    throw new ReplicatorException(
                            "Exception while reading blob data", e);
                }
                finally
                {
                    if (blobStream != null)
                    {
                        try
                        {
                            blobStream.close();
                        }
                        catch (IOException e)
                        {
                        }
                    }
                }
            }
            else
            {
                // Expect a textual field.
                String toString = null;
                if (blob != null)
                {
                    try
                    {
                        toString = new String(blob.getBytes(1,
                                (int) blob.length()));
                    }
                    catch (SerialException e)
                    {
                        throw new ReplicatorException(
                                "Exception while getting blob.getBytes(...)", e);
                    }
                }
                return toString;
            }
        }
        else
            return value.toString();
    }
}