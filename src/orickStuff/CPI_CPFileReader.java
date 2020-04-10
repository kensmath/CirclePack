package orickStuff;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.Vector;

public class CPI_CPFileReader {
    
    Vector<CPI_CPFileSection> section;	
    
    Reader r;
    StreamTokenizer st;
    FileReader fr;
    
    CPI_CPFileReader(String fname) {
	try {
	    section = new Vector<CPI_CPFileSection>();
	    
	    fr = new FileReader(fname);
	    r = new BufferedReader(fr);
	    st = new	 StreamTokenizer(r);	
    	st.resetSyntax();
    	st.wordChars('a', 'z');
    	st.wordChars('A', 'Z');
      	st.wordChars(':', ':');
       	st.wordChars('/', '/');
       	st.wordChars('0', '9');
    	st.wordChars('-', '-');
       	st.wordChars('+', '+');
    	st.wordChars('.', '.');
  	    st.eolIsSignificant(true);
	} catch (IOException e) {
	    System.out.println("error\n");
	}	
    }		
    
    int readNumberList(Vector<Double> V, Vector<Integer> NL){
    	int t;
	boolean more = true;
	t = st.ttype;

	NL.add(0);
	try{
	while ((t == StreamTokenizer.TT_EOL)&&(t!=StreamTokenizer.TT_EOF)) t=st.nextToken();
	if (t==StreamTokenizer.TT_EOF) more = false;
	while ( more ){
	    if (t == StreamTokenizer.TT_WORD )  {
		double temp;
		try {
		    temp = Double.valueOf(st.sval);
		    V.add(temp);
		    t = st.nextToken();
		}catch(NumberFormatException e) {
		    more = false; 
		}
	    } else if (t == StreamTokenizer.TT_EOF) {
	    	more = false;
	    } else if(t==StreamTokenizer.TT_EOL) {
	    	while(t==StreamTokenizer.TT_EOL) {
	    		if (NL.get(NL.size()-1)!=V.size())
	    			NL.add(V.size());
	    		t=st.nextToken();
	    	}
	    } else {
	    	t=st.nextToken();
	    }
	}
	
	//System.out.print(t);
	st.pushBack();
	return t; // return type of next token
	} catch(IOException e){ return t;}
    }
    
    int readSection() {
    	int t = StreamTokenizer.TT_EOF;
    	try {
	    t = st.nextToken();
	    while ((t != StreamTokenizer.TT_EOF)&&(t != StreamTokenizer.TT_WORD)) t = st.nextToken();
	    if ( t == StreamTokenizer.TT_WORD) {
		CPI_CPFileSection sect = new CPI_CPFileSection(st.sval);
		t = st.nextToken();
		while ((t != StreamTokenizer.TT_EOF)&&(t != StreamTokenizer.TT_EOL)){
		    CPI_CPFileToken tok = new CPI_CPFileToken();
		    tok.ttyp = t;
		    if (t == StreamTokenizer.TT_NUMBER) {
			tok.nval = st.nval;
			sect.append(tok);
		    }
		    if (t == StreamTokenizer.TT_WORD) {
			tok.sval = st.sval;
			sect.append(tok);	
		    }
		    if (t != StreamTokenizer.TT_EOL) t = st.nextToken();
		}
       	  	
		if (t != StreamTokenizer.TT_EOF) {
		    while (t == StreamTokenizer.TT_EOL) t=st.nextToken();// had an EOL
		    t = readNumberList(sect.data, sect.lineStarts); 
    		}
		section.add(sect);
	    }
	} catch (IOException e) {
	    System.out.println("error\n");
	}
	return t;
    }

    void testread() {
    	
    	int t = StreamTokenizer.TT_EOF;
    	try {
    		t = st.nextToken();
    		while(t!=StreamTokenizer.TT_EOF){
    			/*
    			 
    			 if((t == StreamTokenizer.TT_NUMBER)||(t == StreamTokenizer.TT_WORD)) {
    				System.out.printf("%d ", t);
    				if (t == StreamTokenizer.TT_NUMBER) {
    					System.out.println(st.nval);
    				}else if (t == StreamTokenizer.TT_WORD) {
    					System.out.println(st.sval);
    				} 
       			}else if (t == StreamTokenizer.TT_EOL) {
       				System.out.println("newline");
       			}
       			*/
 				t = st.nextToken();

    		}
    	} catch (IOException e) {
    			System.out.println("error\n");
    		}	
    		
    }
    void read() {
    	while( readSection() != StreamTokenizer.TT_EOF) ;
     }

    void print() {
    	int i;
    	for (i=0; i<section.size(); i++) {
    		section.get(i).print();
    	}
    }
    
    public static void main(String[] args) {
	System.out.printf("Processing file %s\n", args[0]);
	//System.out.println(Double.valueOf("1e-2"));
	CPI_CPFileReader cfr = new CPI_CPFileReader(args[0]);//"test.text");
	cfr.read();
    cfr.print();
    }

}