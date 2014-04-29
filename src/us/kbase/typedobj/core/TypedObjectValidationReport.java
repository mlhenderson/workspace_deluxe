package us.kbase.typedobj.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import us.kbase.common.service.JsonTokenStream;
import us.kbase.common.service.UObject;
import us.kbase.common.utils.JsonTreeGenerator;
import us.kbase.common.utils.sortjson.KeyDuplicationException;
import us.kbase.common.utils.sortjson.SortedKeysJsonFile;
import us.kbase.common.utils.sortjson.TooManyKeysException;
import us.kbase.typedobj.exceptions.RelabelIdReferenceException;
import us.kbase.typedobj.idref.IdReference;
import us.kbase.typedobj.idref.WsIdReference;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The report generated when a typed object instance is validated.  If the type definition indicates
 * that fields are ID references, those ID references can be extracted from this report.  If a
 * searchable subset flag is set in the type definition, you can extract that too.
 *
 * @author msneddon
 * @author rsutormin
 */
public class TypedObjectValidationReport {

	/**
	 * The list of errors found during validation.  If the object is not valid, this must be non-empty, (although
	 * note that not all validations errors found may be added, for instance, if there are many errors only the
	 * first 10 may be reported).
	 */
	protected List<String> errors;
	
	/**
	 * The typedef author selection indicating in the JSON Schema what data to subset for fast indexing/searching via the WS
	 */
	private JsonNode wsSubsetSelection; //used to be: searchData
	
	/**
	 * The typedef author selection indicating in the JSON Schema what data should be extracted as metadata
	 */
	private MetadataExtractionHandler wsMetadataExtractionHandler;
	
	/**
	 * This is the ID of the type definition used in validation - it is an AbsoluteTypeDefId so you always have full version info
	 */
	private final AbsoluteTypeDefId validationTypeDefId;
	
	/**
	 * Used to keep track of the IDs that were parsed from the object
	 */
	private List<WsIdReference> oldIdRefs;

	/**
	 * We keep a reference to the original instance that was validated so we can later easily rename labels or extract
	 * the ws searchable subset or metadata
	 */
	private final UObject tokenStreamProvider;
	
	private byte[] cacheForSorting;
	
	private File fileForSorting;
	
	private final IdRefNode idRefTree;
	
	/**
	 * keep a jackson mapper around so we don't have to create a new one over and over during subset extraction
	 */
	private static ObjectMapper mapper = new ObjectMapper();
	
	private Map<String,String> absoluteIdRefMapping = Collections.emptyMap();
	
	/**
	 * After validation, assemble the validation result into a report for later use. The report contains
	 * information on validation errors (if any), the IDs found in the object, and information about the
	 * subdata and metadata extraction selection.
	 * 
	 */
	public TypedObjectValidationReport(
			UObject tokenStreamProvider,
			AbsoluteTypeDefId validationTypeDefId, 
			List<String> errors,
			JsonNode wsSubsetSelection,
			JsonNode wsMetadataSelection,
			IdRefNode idRefTree,
			List<WsIdReference> oldIdRefs) {
		this.errors = errors;
		this.wsSubsetSelection = wsSubsetSelection;
		this.wsMetadataExtractionHandler = new MetadataExtractionHandler(wsMetadataSelection);
		this.validationTypeDefId=validationTypeDefId;
		this.oldIdRefs = oldIdRefs;
		this.tokenStreamProvider = tokenStreamProvider;
		this.idRefTree = idRefTree;
	}
	
	/**
	 * Get the absolute ID of the typedef that was used to validate the instance
	 * @return
	 */
	public AbsoluteTypeDefId getValidationTypeDefId() {
		return validationTypeDefId;
	}
	
	/**
	 * @return boolean true if the instance is valid, false otherwise
	 */
	public boolean isInstanceValid() {
		return errors.isEmpty();
	}
	
	/**
	 * Iterate over all items in the report and return the error messages.
	 * @return n_errors
	 */
	public List<String> getErrorMessagesAsList() {
		return errors;
	}
	
	public String[] getErrorMessages() {
		List <String> errMssgs = getErrorMessagesAsList();
		return errMssgs.toArray(new String [errMssgs.size()]);
	}
	
	public List<WsIdReference> getWsIdReferences() {
		return oldIdRefs;
	}
	
	public List<IdReference> getAllIdReferences() {
		return new ArrayList<IdReference>(oldIdRefs);
	}
	
	public List<String> getAllIds() {
		List<String> ret = new ArrayList<String>();
		for (IdReference ref : oldIdRefs)
			ret.add(ref.getId());
		return ret;
	}
	
	public Writable createJsonWritable() {
		return new Writable() {
			@Override
			public void write(OutputStream os) throws IOException {
				if (cacheForSorting != null) {
					os.write(cacheForSorting);
				} else if (fileForSorting != null) {
					InputStream is = new FileInputStream(fileForSorting);
					byte[] buffer = new byte[10000];
					while (true) {
						int len = is.read(buffer);
						if (len < 0)
							break;
						if (len > 0)
							os.write(buffer, 0, len);
					}
					is.close();
				} else {
					relabelWsIdReferencesIntoWriter(os);
				}
			}
			
			@Override
			public void releaseResources() throws IOException {
				if (fileForSorting != null)
					fileForSorting.delete();
			}
		};
	}
	
	/**
	 * Relabel the WS IDs in the original Json document based on the specified set of
	 * ID Mappings, where keys are the original ids and values are the replacement ids.
	 * 
	 * Caution: this relabeling happens in-place, so if you have modified the structure
	 * of the JSON node between validation and invocation of this method, you will likely
	 * get many runtime errors.  You should make a deep copy first if you indent to do this.
	 * 
	 * Memory of the original ids is not changed by this operation.  Thus, if you need
	 * to rename the ids a second time, you must still refer to the id as its original name,
	 * which will not necessarily be the name in the current version of the object.
	 */
	public JsonNode getInstanceAfterIdRefRelabelingForTests() throws RelabelIdReferenceException {
		JsonTreeGenerator jgen = new JsonTreeGenerator(UObject.getMapper());
		try {
			relabelWsIdReferencesIntoGenerator(jgen);
		} catch (IOException ex) {
			throw new RelabelIdReferenceException(ex.getMessage(), ex);
		}
		JsonNode originalInstance = jgen.getTree();
		return originalInstance;
	}
	
	public void checkRelabelingAndSorting(TempFilesManager tfm, long maxInMemorySortSize) 
			throws RelabelIdReferenceException {
		cacheForSorting = null;
		fileForSorting = null;
		try {
			final long[] size = {0L};
			OutputStream sizeOs = new OutputStream() {
				@Override
				public void write(int b) throws IOException {
					size[0]++;
				}
				@Override
				public void write(byte[] b, int off, int len)
						throws IOException {
					size[0] += len;
				}
			};
			JsonGenerator jgen = new JsonFactory().createGenerator(sizeOs);
			boolean sorted = relabelWsIdReferencesIntoGeneratorAndCheckOrder(jgen);
			jgen.close();
			jgen = null;
			if (!sorted) {
				if (maxInMemorySortSize <= 0 || size[0] <= maxInMemorySortSize || tfm == null) {
					ByteArrayOutputStream os = new ByteArrayOutputStream();
					jgen = mapper.getFactory().createGenerator(os);
					relabelWsIdReferencesIntoGenerator(jgen);
					jgen.close();
					cacheForSorting = os.toByteArray();
					os = new ByteArrayOutputStream();
					new SortedKeysJsonFile(cacheForSorting).writeIntoStream(os).close();
					os.close();
					cacheForSorting = os.toByteArray();
				} else {
					File f1 = tfm.generateTempFile("sortinp", "json");
					try {
						jgen = mapper.getFactory().createGenerator(f1, JsonEncoding.UTF8);
						relabelWsIdReferencesIntoGenerator(jgen);
						jgen.close();
						jgen = null;
						fileForSorting = tfm.generateTempFile("sortout", "json");
						FileOutputStream os = new FileOutputStream(fileForSorting);
						new SortedKeysJsonFile(f1).writeIntoStream(os).close();
						os.close();
					} finally {
						f1.delete();
						if (jgen != null)
							jgen.close();
					}
				}
			}
		} catch (KeyDuplicationException ex) {
			throw new RelabelIdReferenceException(ex.getMessage(), ex);
		} catch (TooManyKeysException ex) {
			throw new IllegalStateException("Memory necessary for sorting map keys exceeds the limit " + 
					ex.getMaxMem() + " bytes at " + ex.getPath() + ". To deal with data with so many " +
							"keys you have to sort them on client side.", ex);
		} catch (Exception ex) {
			throw new IllegalStateException(ex.getMessage(), ex);
		}
	}
	
	public void setAbsoluteIdRefMapping(Map<String, String> absoluteIdRefMapping) {
		this.absoluteIdRefMapping = absoluteIdRefMapping;
	}
	
	public Map<String, String> getAbsoluteIdRefMapping() {
		return absoluteIdRefMapping;
	}
	
	private void relabelWsIdReferencesIntoWriter(OutputStream os) throws IOException {
		relabelWsIdReferencesIntoGenerator(new JsonFactory().createGenerator(os));
	}

	private void relabelWsIdReferencesIntoGenerator(JsonGenerator jgen) throws IOException {
		TokenSequenceProvider tsp = createIdRefTokenSequenceProvider();
		try {
			new JsonTokenStreamWriter().writeTokens(tsp, jgen);
			jgen.flush();
		} finally {
			tsp.close();
		}
	}
	
	private TokenSequenceProvider createIdRefTokenSequenceProvider() throws IOException {
		JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
		if (absoluteIdRefMapping.isEmpty())
			return makeTSPfromJTS(jts);
		return new IdRefTokenSequenceProvider(jts, idRefTree, absoluteIdRefMapping);
	}
	
	private boolean relabelWsIdReferencesIntoGeneratorAndCheckOrder(JsonGenerator jgen) throws IOException {
		TokenSequenceProvider tsp = null;
		try {
			if (absoluteIdRefMapping.isEmpty()) {
				JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
				SortCheckingTokenSequenceProvider sortCheck = new SortCheckingTokenSequenceProvider(jts);
				tsp = sortCheck;
				new JsonTokenStreamWriter().writeTokens(sortCheck, jgen);
				return sortCheck.isSorted();
			} else {
				JsonTokenStream jts = tokenStreamProvider.getPlacedStream();
				IdRefTokenSequenceProvider idSubst = new IdRefTokenSequenceProvider(jts, idRefTree, absoluteIdRefMapping);
				tsp = idSubst;
				new JsonTokenStreamWriter().writeTokens(idSubst, jgen);
				idSubst.close();
				return idSubst.isSorted();
			}
		} finally {
			if (tsp != null)
				tsp.close();
		}
	}
	
	private TokenSequenceProvider createTokenSequenceForWsSubset() throws IOException {
		if (cacheForSorting != null || fileForSorting != null) {
			final JsonTokenStream afterSort = new JsonTokenStream(
					cacheForSorting != null ? cacheForSorting : fileForSorting);
			return makeTSPfromJTS(afterSort);
		} else {
			return createIdRefTokenSequenceProvider();
		}
	}

	private TokenSequenceProvider makeTSPfromJTS(final JsonTokenStream jts) {
		return new TokenSequenceProvider() {
			@Override
			public JsonToken nextToken() throws IOException, JsonParseException {
				return jts.nextToken();
			}
			@Override
			public String getText() throws IOException, JsonParseException {
				return jts.getText();
			}
			@Override
			public Number getNumberValue() throws IOException, JsonParseException {
				return jts.getNumberValue();
			}
			@Override
			public void close() throws IOException {
				jts.close();
			}
		};
	}
	
	
	/**
	 * If a searchable ws_subset was defined in the Json Schema, then you can use this method
	 * to extract out the contents.  Note that this method does not perform a deep copy of the data,
	 * so if you extract a subset, then modify the original instance that was validated, it can
	 * (in some but not all cases) modify this subdata as well.  So you should always perform a
	 * deep copy of the original instance if you intend to modify it and subset data has already
	 * been extracted.
	 */
	public JsonNode extractSearchableWsSubset(long maxSubdataSize) {
		if(!isInstanceValid()) {
			return mapper.createObjectNode();
		}
		// Identify what we need to extract
		ObjectNode keys_of  = null;
		ObjectNode fields   = null;
		if (wsSubsetSelection != null) {
			keys_of = (ObjectNode)wsSubsetSelection.get("keys");
			fields = (ObjectNode)wsSubsetSelection.get("fields");
		}
		TokenSequenceProvider tsp = null;
		try {
			tsp = createTokenSequenceForWsSubset();
			JsonNode ret = SearchableWsSubsetExtractor.extractFields(
					tsp, keys_of, fields, maxSubdataSize,null);
			tsp.close();
			return ret;
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		} finally {
			if (tsp != null)
				try { tsp.close(); } catch (Exception ignore) {}
		}
	}

	
	public JsonNode extractSearchableWsSubsetAndMetadata(long maxSubdataSize) {
		ObjectNode returnData = mapper.createObjectNode();
		
		if(!isInstanceValid()) {
			returnData.set("subset", mapper.createObjectNode());
			returnData.set("metadata", mapper.createObjectNode());
			return mapper.createObjectNode();
		}
		
		// Identify what we need to extract
		ObjectNode keys_of  = null;
		ObjectNode fields   = null;
		if (wsSubsetSelection != null) {
			keys_of = (ObjectNode)wsSubsetSelection.get("keys");
			fields = (ObjectNode)wsSubsetSelection.get("fields");
		}
		TokenSequenceProvider tsp = null;
		try {
			tsp = createTokenSequenceForWsSubset();
			JsonNode ret = SearchableWsSubsetExtractor.extractFields(
															tsp, 
															keys_of, fields, maxSubdataSize,
															wsMetadataExtractionHandler);
			tsp.close();
			return ret;
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		} finally {
			if (tsp != null)
				try { tsp.close(); } catch (Exception ignore) {}
		}
		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	@Override
	public String toString() {
		StringBuilder mssg = new StringBuilder();
		mssg.append("TYPED OBJECT VALIDATION REPORT\n");
		mssg.append(" -validated instance against: '"+validationTypeDefId.getTypeString()+"'\n");
		mssg.append(" -status: ");
		if(this.isInstanceValid()) {
			mssg.append("pass\n");
			mssg.append(" -id refs extracted: "+getAllIds().size());
			mssg.append(" -ws id refs extracted: "+getWsIdReferences().size());
		}
		else {
			List<String> errs = getErrorMessagesAsList();
			mssg.append("fail ("+errs.size()+" error(s))\n");
			for(int k=0; k<errs.size(); k++) {
				mssg.append(" -["+(k+1)+"]:"+errs.get(k)+"\n");
			}
		}
		return mssg.toString();
	}
	
	
}
