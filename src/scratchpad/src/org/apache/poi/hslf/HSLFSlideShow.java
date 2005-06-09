
/* ====================================================================
   Copyright 2002-2004   Apache Software Foundation

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
        


package org.apache.poi.hslf;

import java.util.*;
import java.io.*;

import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.poifs.filesystem.POIFSDocument;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;

import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.hpsf.PropertySetFactory;
import org.apache.poi.hpsf.MutablePropertySet;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hpsf.DocumentSummaryInformation;

import org.apache.poi.util.LittleEndian;

import org.apache.poi.hslf.record.*;

/**
 * This class contains the main functionality for the Powerpoint file 
 * "reader". It is only a very basic class for now
 *
 * @author Nick Burch
 */

public class HSLFSlideShow
{
  private InputStream istream;
  private POIFSFileSystem filesystem;

  // Holds metadata on our document
  private SummaryInformation sInf;
  private DocumentSummaryInformation dsInf;
  private CurrentUserAtom currentUser;

  // Low level contents of the file
  private byte[] _docstream;

  // Low level contents
  private Record[] _records;

  /**
   * Constructs a Powerpoint document from fileName. Parses the document 
   * and places all the important stuff into data structures.
   *
   * @param fileName The name of the file to read.
   * @throws IOException if there is a problem while parsing the document.
   */
  public HSLFSlideShow(String fileName) throws IOException
  {
  	this(new FileInputStream(fileName));
  }
  
  /**
   * Constructs a Powerpoint document from an input stream. Parses the 
   * document and places all the important stuff into data structures.
   *
   * @param inputStream the source of the data
   * @throws IOException if there is a problem while parsing the document.
   */
  public HSLFSlideShow(InputStream inputStream) throws IOException
  {
        //do Ole stuff
		this(new POIFSFileSystem(inputStream));
        istream = inputStream;
  }

  /**
   * Constructs a Powerpoint document from a POIFS Filesystem. Parses the 
   * document and places all the important stuff into data structures.
   *
   * @param filesystem the POIFS FileSystem to read from
   * @throws IOException if there is a problem while parsing the document.
   */
  public HSLFSlideShow(POIFSFileSystem filesystem) throws IOException
  {
		this.filesystem = filesystem;

        // Go find a PowerPoint document in the stream
        // Save anything useful we come across
        readFIB();

		// Look for Property Streams:
		readProperties();
  }


  /**
   * Shuts things down. Closes underlying streams etc
   *
   * @throws IOException
   */
  public void close() throws IOException
  {
	if(istream != null) {
		istream.close();
	}
	filesystem = null;
  }


  /**
   * Extracts the main document stream from the POI file then hands off 
   * to other functions that parse other areas.
   *
   * @throws IOException
   */
  private void readFIB() throws IOException
  {
	// Get the main document stream
	DocumentEntry docProps =
		(DocumentEntry)filesystem.getRoot().getEntry("PowerPoint Document");

	// Grab the document stream
	_docstream = new byte[docProps.getSize()];
	filesystem.createDocumentInputStream("PowerPoint Document").read(_docstream);

	// The format of records in a powerpoint file are:
	//   <little endian 2 byte "info">
	//   <little endian 2 byte "type">
	//   <little endian 4 byte "length">
	// If it has a zero length, following it will be another record
	//		<xx xx yy yy 00 00 00 00> <xx xx yy yy zz zz zz zz>
	// If it has a length, depending on its type it may have children or data
	// If it has children, these will follow straight away
	//		<xx xx yy yy zz zz zz zz <xx xx yy yy zz zz zz zz>>
	// If it has data, this will come straigh after, and run for the length
	//      <xx xx yy yy zz zz zz zz dd dd dd dd dd dd dd>
	// All lengths given exclude the 8 byte record header
	// (Data records are known as Atoms)

	// Document should start with:
	//   0F 00 E8 03 ## ## ## ##
    //     (type 1000 = document, info 00 0f is normal, rest is document length)
	//   01 00 E9 03 28 00 00 00
	//     (type 1001 = document atom, info 00 01 normal, 28 bytes long)
	//   80 16 00 00 E0 10 00 00 xx xx xx xx xx xx xx xx
	//   05 00 00 00 0A 00 00 00 xx xx xx
	//     (the contents of the document atom, not sure what it means yet)
	//   (records then follow)

	// When parsing a document, look to see if you know about that type
	//  of the current record. If you know it's a type that has children, 
	//  process the record's data area looking for more records
	// If you know about the type and it doesn't have children, either do
	//  something with the data (eg TextRun) or skip over it
	// If you don't know about the type, play safe and skip over it (using
	//  its length to know where the next record will start)
	//
	// For now, this work is handled by Record.findChildRecords

	_records = Record.findChildRecords(_docstream,0,_docstream.length);
  }


  /**
   * Find the properties from the filesystem, and load them
   */
  public void readProperties() {
	// DocumentSummaryInformation
	dsInf = (DocumentSummaryInformation)getPropertySet("\005DocumentSummaryInformation");

	// SummaryInformation
	sInf = (SummaryInformation)getPropertySet("\005SummaryInformation");

	// Current User
	try {
		currentUser = new CurrentUserAtom(filesystem);
	} catch(IOException ie) {
		System.err.println("Error finding Current User Atom:\n" + ie);
		currentUser = new CurrentUserAtom();
	}
  }


  /** 
   * For a given named property entry, either return it or null if
   *  if it wasn't found
   */
  public PropertySet getPropertySet(String setName) {
	DocumentInputStream dis;
	try {
		// Find the entry, and get an input stream for it
		dis = filesystem.createDocumentInputStream(setName);
	} catch(IOException ie) {
		// Oh well, doesn't exist
		System.err.println("Error getting property set with name " + setName + "\n" + ie);
		return null;
	}

	try {
		// Create the Property Set
		PropertySet set = PropertySetFactory.create(dis);
		return set;
	} catch(IOException ie) {
		// Must be corrupt or something like that
		System.err.println("Error creating property set with name " + setName + "\n" + ie);
	} catch(org.apache.poi.hpsf.HPSFException he) {
		// Oh well, doesn't exist
		System.err.println("Error creating property set with name " + setName + "\n" + he);
	}
	return null;
  }


  /**
   * Writes out the slideshow file the is represented by an instance of
   *  this class
   * @param out The OutputStream to write to.
   *  @throws IOException If there is an unexpected IOException from the passed
   *            in OutputStream
   */
   public void write(OutputStream out) throws IOException {
	// Get a new Filesystem to write into
	POIFSFileSystem outFS = new POIFSFileSystem();

	// Write out the Property Streams
	if(sInf != null) {
		writePropertySet("\005SummaryInformation",sInf,outFS);
	}
	if(dsInf != null) {
		writePropertySet("\005DocumentSummaryInformation",dsInf,outFS);
	}


	// For position dependent records, hold where they were and now are
	// As we go along, update, and hand over, to any Position Dependent
	//  records we happen across
	Hashtable oldToNewPositions = new Hashtable();

	// Write ourselves out
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	for(int i=0; i<_records.length; i++) {
		// For now, we're only handling PositionDependentRecord's that
		//  happen at the top level.
		// In future, we'll need the handle them everywhere, but that's
		//  a bit trickier
		if(_records[i] instanceof PositionDependentRecord) {
			PositionDependentRecord pdr = (PositionDependentRecord)_records[i];
			int oldPos = pdr.getLastOnDiskOffset();
			int newPos = baos.size();
			pdr.setLastOnDiskOffset(newPos);
			oldToNewPositions.put(new Integer(oldPos),new Integer(newPos));
			pdr.updateOtherRecordReferences(oldToNewPositions);
		}

		// Finally, write out
		_records[i].writeOut(baos);
	}
	// Update our cached copy of the bytes that make up the PPT stream
	_docstream = baos.toByteArray();

	// Write the PPT stream into the POIFS layer
	ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
	outFS.createDocument(bais,"PowerPoint Document");


	// Update and write out the Current User atom
	int oldLastUserEditAtomPos = (int)currentUser.getCurrentEditOffset();
	Integer newLastUserEditAtomPos = (Integer)oldToNewPositions.get(new Integer(oldLastUserEditAtomPos));
	if(newLastUserEditAtomPos == null) {
		throw new RuntimeException("Couldn't find the new location of the UserEditAtom that used to be at " + oldLastUserEditAtomPos);
	}
	currentUser.setCurrentEditOffset(newLastUserEditAtomPos.intValue());
	currentUser.writeToFS(outFS);


	// Send the POIFSFileSystem object out to the underlying stream
	outFS.writeFilesystem(out);
   }


  /**
   * Writes out a given ProperySet
   */
  private void writePropertySet(String name, PropertySet set, POIFSFileSystem fs) throws IOException {
	try {
		MutablePropertySet mSet = new MutablePropertySet(set);
		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		mSet.write(bOut);
		byte[] data = bOut.toByteArray();
		ByteArrayInputStream bIn = new ByteArrayInputStream(data);
		fs.createDocument(bIn,name);
		System.out.println("Wrote property set " + name + " of size " + data.length);
	} catch(org.apache.poi.hpsf.WritingNotSupportedException wnse) {
		System.err.println("Couldn't write property set with name " + name + " as not supported by HPSF yet");
	}
  }


  /* ******************* fetching methods follow ********************* */


  /**
   * Returns an array of all the records found in the slideshow
   */
  public Record[] getRecords() { return _records; }

  /**
   * Returns an array of the bytes of the file. Only correct after a
   *  call to open or write - at all other times might be wrong!
   */
  public byte[] getUnderlyingBytes() { return _docstream; }

  /** 
   * Fetch the Document Summary Information of the document
   */
  public DocumentSummaryInformation getDocumentSummaryInformation() { return dsInf; }

  /** 
   * Fetch the Summary Information of the document
   */
  public SummaryInformation getSummaryInformation() { return sInf; }

 /**
  * Fetch the Current User Atom of the document
  */
 public CurrentUserAtom getCurrentUserAtom() { return currentUser; }
}
