package org.likeit.transformation.stream;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/*
 * TODO utiliser une methode fillBuffer pour remplire le buffer et a cet endroit 
 * mettre a jour la position si necessaire, faire un truc plus propre pour calculer
 * la ligne/colonne de l'erreur (mieux que la position...)
 */
public class JsonReader implements ObjectReader {
	protected static final int BEGIN_ARRAY = '[';
	protected static final int END_ARRAY = ']';
	protected static final int BEGIN_OBJECT = '{';
	protected static final int END_OBJECT = '}';
	protected static final int QUOTE = '"';
	
	protected static final int VALUE_SEPARATOR = ',';
	protected static final int NAME_SEPARATOR = ':';
	protected final static String NULL_VALUE = "null";	
	
	protected static final int[] SKIPPED_TOKENS;
	static {
		SKIPPED_TOKENS = new int[128];
		SKIPPED_TOKENS['\t'] = 1;
		SKIPPED_TOKENS['\b'] = 1;
		SKIPPED_TOKENS['\n'] = 1;
		SKIPPED_TOKENS['\r'] = 1;
		SKIPPED_TOKENS['\f'] = 1;
		SKIPPED_TOKENS[' '] = 1;
	}
	
	 /**
	 * Recupere dans Jackson
     * Lookup table for the first 128 Unicode characters (7-bit ASCII)
     * range. For actual hex digits, contains corresponding value;
     * for others -1.
     */
   private final static int[] sHexValues = new int[128];
    static {
        Arrays.fill(sHexValues, -1);
        for (int i = 0; i < 10; ++i) {
            sHexValues['0' + i] = i;
        }
        for (int i = 0; i < 6; ++i) {
            sHexValues['a' + i] = 10 + i;
            sHexValues['A' + i] = 10 + i;
        }
    }
	
	private final Reader reader;
	// buffer size doit etre > 5 (pour pouvoir contenir FALSE, TRUE et NULL en entier)
	private char[] buffer = new char[1024];
	private int position;
	private int cursor;
	private int buflen;
	private StringBuilder sb = new StringBuilder();
	private String currentName;
	private String currentValue;
	private TokenType tokenType;
	private boolean checkedNext = false;
	private boolean hasNext = false;
	private boolean _first = false;
	
	private Deque<JsonType> _ctx = new ArrayDeque<JsonType>(16);
	{
		_ctx.push(JsonType.EMPTY);
	}

	public JsonReader(String source) {
		this(new StringReader(source));
	}
	
	public JsonReader(Reader reader) {
		this.reader = reader;
	}
	
	@Override
	public ObjectReader beginArray() throws IOException {
		begin(BEGIN_ARRAY, JsonType.ARRAY);
		return this;
	}

	@Override
	public ObjectReader beginObject() throws IOException {
		begin(BEGIN_OBJECT, JsonType.OBJECT);
		return this;
	}
	
	private void begin(int character, JsonType type) throws IOException {
		int token = readNextToken(true);
		checkIllegalEnd(token);
		if ( character == token ) {
			_ctx.push(type);
		} else throw newWrongTokenException(character, token, position-1);
		_first = true;
		checkedNext = false;
		hasNext = false;
	}

	@Override
	public ObjectReader endArray() throws IOException {
		end(END_ARRAY, JsonType.ARRAY);
		return this;
	}

	@Override
	public ObjectReader endObject() throws IOException {
		end(END_OBJECT, JsonType.OBJECT);
		return this;
	}

	private void end(int character, JsonType type) throws IOException {
		int token = readNextToken(true);
		checkIllegalEnd(token);
		if ( character == token && type == _ctx.peek() ) {
			_ctx.pop();
		} else throw newWrongTokenException(character, token, position-1);
		_first = false;
		checkedNext = false;
		hasNext = false;
	}
	
	@Override
	public boolean hasNext() throws IOException {
		if ( checkedNext ) return hasNext;
		else {
			int token = readNextToken(false);
			checkIllegalEnd(token);
			hasNext = (_first && (QUOTE == token || token == BEGIN_OBJECT || token == BEGIN_ARRAY || (token > 47 && token < 58) || token == 45 || token == 110 || token == 116 || token == 102 ))
					|| token == VALUE_SEPARATOR;
			checkedNext = true;
			
			if ( !_first && hasNext ) {
				increment();
			}
			
			return hasNext;
		}
	}
	
	@Override
	public String name() {
		return currentName;
	}
	
	@Override
	public String value() {
		return currentValue;
	}
	
	@Override
	public TokenType getTokenType() {
		return tokenType;
	}

	@Override
	public TokenType next() throws IOException {
		checkedNext = false;
		hasNext = false;
		_first = false;
		currentName = null;
		currentValue = null;
		
		int token = readNextToken(false);
		
		if ( token == VALUE_SEPARATOR ) increment();
		else if ( JsonType.ARRAY == _ctx.peek() ) {
			if ( token == BEGIN_ARRAY ) return setTokenType(TokenType.ARRAY);
			if ( token == BEGIN_OBJECT ) return setTokenType(TokenType.OBJECT);
		}
		
		if ( JsonType.OBJECT == _ctx.peek() ) {
			consumeString();
			
			currentName = sb.toString();
			sb.setLength(0);
			
			if ( readNextToken(true) != NAME_SEPARATOR ) throw newMisplacedTokenException(cursor-1, position-1);
		}
		
		token = readNextToken(false);
		TokenType tokenType = null;
		if ( token == QUOTE ) {
			consumeString();
			tokenType = TokenType.STRING;
		} else if ( token == BEGIN_ARRAY ) return setTokenType(TokenType.ARRAY);
		else if ( token == BEGIN_OBJECT ) return setTokenType(TokenType.OBJECT); 
		else tokenType = consumeLiteral();
		
		currentValue = sb.toString();
		sb.setLength(0);
		
		return setTokenType(tokenType);
	}
	
	private TokenType setTokenType(TokenType tokenType) {
		this.tokenType = tokenType;
		return tokenType;
	}

	private void consumeString() throws IOException {
		if ( readNextToken(true) != QUOTE ) throw newMisplacedTokenException(cursor-1, position-1);
		
		while (buflen > -1 ) {
			if ( cursor >= buflen ) {
				buflen = reader.read(buffer);
				cursor = 0;
				// TODO calc pos?
			}

			int i = cursor;
			for ( ; i < buflen; ) {
				if ( buffer[i] == '\\' ) {
					// TODO calc position
					sb.append(buffer, cursor, i-cursor);
					cursor = i;
					increment();
					sb.append(readEscaped());
					i = cursor;
				}
				else if ( buffer[i] == QUOTE ) break;
				else  i++;
			}
			
			sb.append(buffer, cursor, i-cursor);
			position += i - cursor + 1;
			cursor = i + 1;
			if ( i < buflen && buffer[i] == QUOTE ) return;
		}
	}

	private TokenType consumeLiteral() throws IOException {
		if ( cursor >= buflen ) {
			buflen = reader.read(buffer);
			cursor = 0;
		}
		
		int token = buffer[cursor];
		
		if ( (token > 47 && token < 58) || token == 45 ) {
			TokenType tokenType = null;
			if ( token == 45 ) {
				sb.append('-');
				increment();
			}
			consumeInt();
			if ( buffer[cursor] == 46 ) {
				sb.append('.');
				increment();
				consumeInt();
				tokenType = TokenType.DOUBLE;
			} else tokenType = TokenType.INTEGER;
			
			if ( (buflen-cursor) < 2 ) {
				System.arraycopy(buffer, cursor, buffer, 0, buflen-cursor);
				buflen = cursor + reader.read(buffer, cursor, cursor);
				cursor = 0;
			}
			
			char ctoken = buffer[cursor];
			if (ctoken == 'e' || ctoken == 'E') {
				increment();
				ctoken = buffer[cursor];
			     if (ctoken == '+' || ctoken == '-') {
			    	 sb.append(buffer, cursor-1, 2);
			    	 increment();
			    	 consumeInt();
			      }
			}
			
			return tokenType;
		} else {
			if ( (buflen-cursor) < 5 ) {
				System.arraycopy(buffer, cursor, buffer, 0, buflen-cursor);
				buflen = cursor + reader.read(buffer, cursor, cursor);
				cursor = 0;
			}
			
			if ( (buffer[cursor] == 'N' || buffer[cursor] == 'n')
				&& (buffer[cursor+1] == 'U' || buffer[cursor+1] == 'u')
				&& (buffer[cursor+2] == 'L' || buffer[cursor+2] == 'l')
				&& (buffer[cursor+3] == 'L' || buffer[cursor+3] == 'l')) {
				sb.append(NULL_VALUE);
				cursor += 4;
				position +=  4;
				return TokenType.NULL;
			} else if ( (buffer[cursor] == 'T' || buffer[cursor] == 't')
						&& (buffer[cursor+1] == 'R' || buffer[cursor+1] == 'r')
						&& (buffer[cursor+2] == 'U' || buffer[cursor+2] == 'u')
						&& (buffer[cursor+3] == 'E' || buffer[cursor+3] == 'e')) {
				sb.append("true");
				cursor += 4;
				position +=  4;
				return TokenType.BOOLEAN;
			} else if ( (buffer[cursor] == 'F' || buffer[cursor] == 'f')
						&& (buffer[cursor+1] == 'A' || buffer[cursor+1] == 'a')
						&& (buffer[cursor+2] == 'L' || buffer[cursor+2] == 'l')
						&& (buffer[cursor+3] == 'S' || buffer[cursor+3] == 's')
						&& (buffer[cursor+4] == 'E' || buffer[cursor+4] == 'e')) {
				sb.append("false");
				cursor += 5;
				position +=  5;
				return TokenType.BOOLEAN;
			} else {
				throw new IllegalStateException("Illegal character around position " + position + " awaited for literal (number, boolean or null)");
			}
		}
	}
	
	private void consumeInt() throws IOException {
		boolean stop = false;
		while (buflen > -1 ) {
			if ( cursor >= buflen ) {
				buflen = reader.read(buffer);
				cursor = 0;
			}

			int i = cursor;
			for ( ; i < buflen; i++ ) {
				if ( (buffer[i] < 48 || buffer[i] > 57) ) {
					stop = true;
					break;
				}
			}
			
			sb.append(buffer, cursor, i-cursor);
			position += i - cursor;
			cursor = i;
			if ( stop ) return;
		}
	}
	
	private int readNextToken(boolean consume) throws IOException {
		boolean stop = false;
		int oldCursor = cursor;
		
		while ( buflen > -1 ) {
			if ( cursor >= buflen ) {
				buflen = reader.read(buffer);
				cursor = 0;
			}
			
			for ( ; cursor < buflen; cursor++ ) {
				if ( buffer[cursor] < 128 && SKIPPED_TOKENS[buffer[cursor]] == 0 ) {
					stop = true;
					break;
				}
			}
			
			// TODO attention c'est faux
			position += cursor - oldCursor;
			oldCursor = cursor - 1;
			
			if ( stop ) {
				if ( consume ) {
					cursor++;
					position++;
					return buffer[cursor-1];
				} else return buffer[cursor];
			}
		}
		
		return -1;
	}
	
	 private char readEscaped() throws IOException {
		
    	if ( cursor >= buflen ) {
			buflen = reader.read(buffer);
			cursor = 0;
			checkIllegalEnd(buflen);
		}
    	
    	char token = buffer[cursor];
    	increment();
        switch (token) {
            case 'b':
                return '\b';
            case 't':
                return '\t';
            case 'n':
                return '\n';
            case 'f':
                return '\f';
            case 'r':
                return '\r';
            case QUOTE:
            case '/':
            case '\\':
                return (char) token;
    
            case 'u':
                break;
    
            default:
                throw newMisplacedTokenException(cursor-1, position-1);
        }

        int value = 0;
        if ( (buflen-cursor) < 4 ) {
			System.arraycopy(buffer, cursor, buffer, 0, buflen-cursor);
			buflen = cursor + reader.read(buffer, cursor, cursor);
			cursor = 0;
		}
        for (int i = 0; i < 4; ++i) {
            int ch = buffer[cursor++];
            int digit = (ch > 127) ? -1 : sHexValues[ch];
            if (digit < 0) {
                throw new IllegalStateException("Wrong character '"+ch+"' expected a hex-digit for character escape sequence");
            }
            value = (value << 4) | digit;
        }

        return (char)value;
    }
	
	private void increment() {
		cursor++;
		position++;
	}
	
	private IllegalStateException newWrongTokenException(int awaitedChar, int token, int position) {
		return new IllegalStateException("Illegal character at position " + position + " expected " + (char)awaitedChar + " but read '" + (char)token + "' !");
	}
	
	private IllegalStateException newMisplacedTokenException(int cursor, int position) {
		return new IllegalStateException("Encountred misplaced character '" + buffer[cursor] + "' at position " + position);
	}
	
	private void checkIllegalEnd(int token) {
		if ( token == -1 && JsonType.EMPTY != _ctx.peek() ) 
			throw new IllegalStateException("Incomplete data or malformed json : encoutered end of stream!");
	}
	
}