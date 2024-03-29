/*
 * Copyright 2010 sasc
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
 */
package sasc.iso7816;

import java.util.Arrays;
import sasc.iso7816.Tag;

/**
 *
 * @author sasc
 */
public class TagAndLength {
    private Tag tag;
    private int length;

    public TagAndLength(Tag tag, int length){
        this.tag = tag;
        this.length = length;
    }

    public Tag getTag(){
        return tag;
    }

    public int getLength(){
        return length;
    }
    
    public byte[] getBytes(){
        byte[] tagBytes = tag.getTagBytes();
        byte[] tagAndLengthBytes = Arrays.copyOf(tagBytes, tagBytes.length + 1);
        tagAndLengthBytes[tagAndLengthBytes.length-1] = (byte)length;
        return tagAndLengthBytes;
    }

    @Override
    public String toString(){
        return tag.toString() + " " + length;
    }

}
