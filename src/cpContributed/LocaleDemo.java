package cpContributed;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;
import java.util.Vector;

/**
 * LocaleDemo prints the same random values for every installed Locale on the
 * user's JVM. It is intended to demonstrate how to properly use the Locale
 * system to localize formatting for international users.
 * 
 * @author Alex Fawkes
 */
public class LocaleDemo {
	private static final String NEWLINE = System.getProperty("line.separator");
	private Random random;
	private Locale defaultLocale;
	private Vector<Integer> integers;
	private Vector<Double> doubles;
	private Vector<Double> percentages;
	private Vector<Double> currency;
	
	public LocaleDemo() {
		random = new Random();
		// Get the default Locale for the user's JVM. Set to en_US (English, United States) for the author.
		defaultLocale = Locale.getDefault();
		integers = new Vector<Integer>();
		doubles = new Vector<Double>();
		percentages = new Vector<Double>();
		currency = new Vector<Double>();
		
		// Fill the vectors with random values.
		for (int i = 0; i < 5; i++) {
			integers.add(random.nextInt());
			doubles.add(random.nextDouble());
			percentages.add(random.nextDouble());
			currency.add(random.nextDouble());
		}
	}
	
	public void runDemo() {
		// Get an array of all installed Locales for the user's JVM.
		Locale[] installedLocales = Locale.getAvailableLocales();
		
		// Print the random values for all installed Locales, starting with the default.
		printLocale(defaultLocale);
		for (int i = 0; i < installedLocales.length; i++) {
			if (installedLocales[i].equals(defaultLocale)) continue;
			printLocale(installedLocales[i]);
		}
	}
	
	private void printLocale(Locale locale) {
		// Set the default Locale for the JVM to the passed argument.
		Locale.setDefault(locale);
		
		// Get the correct formatting information for the current default locale.
		// Get the general number format.
		NumberFormat nf = NumberFormat.getInstance();
		// Get the percentage format.
		NumberFormat pf = NumberFormat.getPercentInstance();
		// Get the currency format.
		NumberFormat cf = NumberFormat.getCurrencyInstance();
		
		// Print the block header for this Locale.
		System.out.println("INSTALLED LOCALE");
		// Print the language, country, and variant for this Locale.
		// Will be empty if unspecified. Do not print empty strings.
		if (!locale.getDisplayLanguage().isEmpty())
			System.out.println("Language:\t" + locale.getDisplayLanguage());
		if (!locale.getDisplayCountry().isEmpty())
			System.out.println("Country: \t" + locale.getDisplayCountry());
		if (!locale.getDisplayVariant().isEmpty())
			System.out.println("Variant: \t" + locale.getDisplayVariant());
		// Print the programmatic code for this Locale.
		// These codes may be used to initialize Locale objects.
		// For example, new Locale("en", "US") will return the Locale object for English, United States.
		System.out.println("Code:    \t" + locale.toString());
		
		// Print out the random integers using the general number format.
		System.out.println("Integers:");
		for (int i = 0; i < integers.size(); i++) {
			System.out.println("\t\t" + nf.format(integers.get(i)));
		}
		
		// Print out the random doubles using the general number format.
		System.out.println("Doubles:");
		for (int i = 0; i < doubles.size(); i++) {
			System.out.println("\t\t" + nf.format(doubles.get(i)));
		}
		
		// Print out the random percentages using the percentage format.
		System.out.println("Percentages:");
		for (int i = 0; i < percentages.size(); i++) {
			System.out.println("\t\t" + pf.format(percentages.get(i)));
		}
		
		// Print out the random currency values using the currency format.
		System.out.println("Currency:");
		for (int i = 0; i < currency.size(); i++) {
			System.out.println("\t\t" + cf.format(currency.get(i)));
		}
	
		System.out.println(NEWLINE);
	}
	
	public static void main(String[] args) {
		// Load up a fresh instance and run it.
		LocaleDemo localeDemo = new LocaleDemo();
		localeDemo.runDemo();
	}
}
