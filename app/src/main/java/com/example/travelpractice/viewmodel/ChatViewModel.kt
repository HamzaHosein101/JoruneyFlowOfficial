package com.example.travelpractice.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelpractice.ai.TravelAgent
import com.example.travelpractice.handlers.ChatAction
import com.example.travelpractice.handlers.ChatActionHandler
import com.example.travelpractice.model.Message
import com.example.travelpractice.model.MessageSender
import com.example.travelpractice.model.MessageType
import com.example.travelpractice.repository.ChatRepository
import com.example.travelpractice.utils.IntentDetector
import com.example.travelpractice.utils.AppDataFetcher
import com.example.travelpractice.utils.FlightSearchHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ChatViewModel(
    private val geminiApiKey: String,
    private val chatActionHandler: ChatActionHandler
) : ViewModel() {

    private val travelAgent = TravelAgent(geminiApiKey)
    private val chatRepository = ChatRepository()
    private val intentDetector = IntentDetector
    private val dataFetcher = AppDataFetcher()


    private val flightHelper = FlightSearchHelper(
        clientId = "F89gSTzjGAmDYL2bLu3aYs6NRjyYL0b1",
        clientSecret = "sBZVENdviUxICZTQ"
    )


    private var currentTripId: String? = null

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isTyping = MutableLiveData<Boolean>(false)
    val isTyping: LiveData<Boolean> = _isTyping

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadChatHistory()
        loadMostRecentTrip()
    }


    private fun loadMostRecentTrip() {
        viewModelScope.launch {
            currentTripId = dataFetcher.getMostRecentTripId()
            Log.d("ChatViewModel", "Auto-selected trip: $currentTripId")
        }
    }


    private fun loadChatHistory() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = chatRepository.loadMessages()
                if (result.isSuccess) {
                    val loadedMessages = result.getOrNull() ?: emptyList()
                    if (loadedMessages.isEmpty()) {
                        addWelcomeMessage()
                    } else {
                        _messages.value = loadedMessages
                    }
                } else {
                    addWelcomeMessage()
                }
            } catch (e: Exception) {
                addWelcomeMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }


    private fun addWelcomeMessage() {
        val welcomeMessage = Message(
            id = System.currentTimeMillis(),
            text = "Hello! I am your AI Helper. How can I help you?",
            sender = MessageSender.BOT,
            timestamp = Date(),
            type = MessageType.GREETING
        )

        viewModelScope.launch {
            addMessage(welcomeMessage)
            chatRepository.saveMessage(welcomeMessage)
        }
    }


    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = Message(
            id = System.currentTimeMillis(),
            text = text.trim(),
            sender = MessageSender.USER,
            timestamp = Date(),
            type = MessageType.GENERAL
        )

        addMessage(userMessage)

        viewModelScope.launch {
            chatRepository.saveMessage(userMessage)
        }

        val detectedIntent = intentDetector.detectIntent(text)

        // Debug logs
        Log.d("ChatViewModel", "===========================================")
        Log.d("ChatViewModel", "User query: $text")
        Log.d("ChatViewModel", "Detected intent: ${detectedIntent.intent}")
        Log.d("ChatViewModel", "Confidence: ${detectedIntent.confidence}")
        Log.d("ChatViewModel", "===========================================")

        when (detectedIntent.intent) {
            IntentDetector.Intent.EXPENSE_TRACKER -> {
                Log.d("ChatViewModel", "→ Handling EXPENSE_TRACKER")
                handleExpenseRequest(detectedIntent)
            }
            IntentDetector.Intent.ITINERARY -> {
                Log.d("ChatViewModel", "→ Handling ITINERARY")
                handleItineraryRequest(detectedIntent)
            }
            IntentDetector.Intent.CHECKLIST -> {
                Log.d("ChatViewModel", "→ Handling CHECKLIST")
                handleChecklistRequest(detectedIntent)
            }
            IntentDetector.Intent.FLIGHT_SEARCH -> {
                Log.d("ChatViewModel", "→ Handling FLIGHT_SEARCH")
                handleFlightSearch(detectedIntent, text)
            }
            IntentDetector.Intent.HOTEL_SEARCH -> {
                Log.d("ChatViewModel", "→ Handling HOTEL_SEARCH")
                handleHotelSearch(detectedIntent, text)
            }
            else -> {
                Log.d("ChatViewModel", "→ Handling GENERAL_CHAT (else branch)")
                getAIResponse(text, detectedIntent)
            }
        }
    }


    private fun handleExpenseRequest(detectedIntent: IntentDetector.DetectedIntent) {
        _isTyping.value = true

        viewModelScope.launch {
            try {
                val tripId = currentTripId

                if (tripId == null) {
                    val tripsMessage = dataFetcher.getTripsListMessage()
                    val botMessage = Message(
                        id = System.currentTimeMillis(),
                        text = tripsMessage,
                        sender = MessageSender.BOT,
                        timestamp = Date(),
                        type = MessageType.BUDGET
                    )
                    addBotMessage(botMessage)
                } else {
                    val expenseData = dataFetcher.getExpenseSummary(tripId)

                    val action = chatActionHandler.handleIntent(detectedIntent)
                    val actionOptions = if (action is ChatAction.ShowOptions) action.options else null

                    val botMessage = Message(
                        id = System.currentTimeMillis(),
                        text = expenseData,
                        sender = MessageSender.BOT,
                        timestamp = Date(),
                        type = MessageType.BUDGET,
                        actionOptions = actionOptions
                    )

                    addBotMessage(botMessage)
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isTyping.value = false
            }
        }
    }


    private fun handleItineraryRequest(detectedIntent: IntentDetector.DetectedIntent) {
        _isTyping.value = true

        viewModelScope.launch {
            try {
                val tripId = currentTripId

                if (tripId == null) {
                    val tripsMessage = dataFetcher.getTripsListMessage()
                    val botMessage = Message(
                        id = System.currentTimeMillis(),
                        text = tripsMessage,
                        sender = MessageSender.BOT,
                        timestamp = Date(),
                        type = MessageType.ITINERARY
                    )
                    addBotMessage(botMessage)
                } else {
                    val itineraryData = dataFetcher.getItinerarySummary(tripId)

                    val action = chatActionHandler.handleIntent(detectedIntent)
                    val actionOptions = if (action is ChatAction.ShowOptions) action.options else null

                    val botMessage = Message(
                        id = System.currentTimeMillis(),
                        text = itineraryData,
                        sender = MessageSender.BOT,
                        timestamp = Date(),
                        type = MessageType.ITINERARY,
                        actionOptions = actionOptions
                    )

                    addBotMessage(botMessage)
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isTyping.value = false
            }
        }
    }


    private fun handleChecklistRequest(detectedIntent: IntentDetector.DetectedIntent) {
        _isTyping.value = true

        viewModelScope.launch {
            try {
                val tripId = currentTripId

                if (tripId == null) {
                    val tripsMessage = dataFetcher.getTripsListMessage()
                    val botMessage = Message(
                        id = System.currentTimeMillis(),
                        text = tripsMessage,
                        sender = MessageSender.BOT,
                        timestamp = Date(),
                        type = MessageType.PACKING
                    )
                    addBotMessage(botMessage)
                } else {
                    val checklistData = dataFetcher.getChecklistSummary(tripId)

                    val action = chatActionHandler.handleIntent(detectedIntent)
                    val actionOptions = if (action is ChatAction.ShowOptions) action.options else null

                    val botMessage = Message(
                        id = System.currentTimeMillis(),
                        text = checklistData,
                        sender = MessageSender.BOT,
                        timestamp = Date(),
                        type = MessageType.PACKING,
                        actionOptions = actionOptions
                    )

                    addBotMessage(botMessage)
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isTyping.value = false
            }
        }
    }


    private fun handleFlightSearch(detectedIntent: IntentDetector.DetectedIntent, query: String) {
        Log.d("ChatViewModel", "handleFlightSearch called with query: $query")
        _isTyping.value = true

        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Parsing flight query...")
                val result = parseAndSearchFlights(query)
                Log.d("ChatViewModel", "Flight search result (first 100 chars): ${result.take(100)}...")

                val botMessage = Message(
                    id = System.currentTimeMillis(),
                    text = result,
                    sender = MessageSender.BOT,
                    timestamp = Date(),
                    type = MessageType.FLIGHTS
                )

                addBotMessage(botMessage)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in handleFlightSearch", e)
                handleError(e)
            } finally {
                _isTyping.value = false
            }
        }
    }


    private fun handleHotelSearch(detectedIntent: IntentDetector.DetectedIntent, query: String) {
        Log.d("ChatViewModel", "handleHotelSearch called with query: $query")
        _isTyping.value = true

        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Parsing hotel query...")
                val result = parseAndSearchHotelsAmadeus(query)
                Log.d("ChatViewModel", "Hotel search result (first 100 chars): ${result.take(100)}...")

                val botMessage = Message(
                    id = System.currentTimeMillis(),
                    text = result,
                    sender = MessageSender.BOT,
                    timestamp = Date(),
                    type = MessageType.ACCOMMODATION
                )

                addBotMessage(botMessage)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in handleHotelSearch", e)
                handleError(e)
            } finally {
                _isTyping.value = false
            }
        }
    }


    private suspend fun parseAndSearchFlights(query: String): String {
        val lowerQuery = query.lowercase()
        Log.d("ChatViewModel", "Parsing flight query: $lowerQuery")

        // ✅ Extract travel class
        val travelClass = when {
            lowerQuery.contains("business class") || lowerQuery.contains("business") -> "business"
            lowerQuery.contains("first class") || lowerQuery.contains("first") -> "first"
            lowerQuery.contains("premium economy") -> "premium_economy"
            else -> null
        }

        // ✅ Extract airline preference
        val airline = extractAirline(lowerQuery)

        // ✅ Check for direct/non-stop flights
        val nonStop = lowerQuery.contains("direct") || lowerQuery.contains("non-stop") || lowerQuery.contains("nonstop")

        // ✅ Check for round-trip
        val isRoundTrip = lowerQuery.contains("round trip") || lowerQuery.contains("roundtrip") ||
                lowerQuery.contains("return")

        // ✅ Extract cheapest preference
        val cheapestOnly = lowerQuery.contains("cheapest") || lowerQuery.contains("lowest price")

        // ✅ NEW: Try to extract origin and destination (city names OR codes)
        val (origin, destination) = extractFlightLocations(lowerQuery)

        if (origin == null || destination == null) {
            Log.d("ChatViewModel", "Could not extract origin/destination")
            return """
                To search for flights, please specify origin and destination.
                
                Example queries:
                • "Flights from New York to Paris"
                • "Fly from London to Tokyo"
                • "Find flights from JFK to LAX"
                • "Round trip from Miami to Barcelona"
                • "Direct flights from San Francisco to Chicago"
                
                You can use city names OR airport codes! 🌍
            """.trimIndent()
        }

        Log.d("ChatViewModel", "Found locations - Origin: $origin, Destination: $destination")

        // ✅ Get airport codes dynamically
        val originCode = getAirportCode(origin)
        val destinationCode = getAirportCode(destination)

        if (originCode == null || destinationCode == null) {
            return """
                ❌ Could not find airport for: ${if (originCode == null) origin else destination}
                
                Please try:
                • Using a major city name
                • Using the airport code (e.g., JFK, LAX)
                • Being more specific (e.g., "New York JFK" instead of just "New York")
            """.trimIndent()
        }

        Log.d("ChatViewModel", "Airport codes - Origin: $originCode, Destination: $destinationCode")
        Log.d("ChatViewModel", "Travel class: $travelClass, Airline: $airline, Non-stop: $nonStop, Round-trip: $isRoundTrip")

        // ✅ Extract dates (departure and return)
        val departureDate = extractDate(lowerQuery, "departure") ?: getDefaultDate(7)
        val returnDate = if (isRoundTrip) {
            extractDate(lowerQuery, "return") ?: getDefaultDate(14)
        } else null

        Log.d("ChatViewModel", "Departure: $departureDate, Return: $returnDate")
        Log.d("ChatViewModel", "Calling Amadeus Flight API...")

        return flightHelper.searchFlights(
            origin = originCode,
            destination = destinationCode,
            departureDate = departureDate,
            returnDate = returnDate,
            adults = 1,
            maxResults = if (cheapestOnly) 10 else 5,
            travelClass = travelClass,
            preferredAirline = airline,
            nonStop = if (nonStop) true else null
        )
    }


    private suspend fun parseAndSearchHotelsAmadeus(query: String): String {
        val lowerQuery = query.lowercase()
        Log.d("ChatViewModel", "Parsing hotel query for Amadeus: $lowerQuery")


        val cityCode = extractCityCode(lowerQuery)

        if (cityCode == null) {
            Log.d("ChatViewModel", "No city found in query")
            return """
                To search for hotels, please specify a city.
                
                Example queries:
                • "Find hotels in Paris"
                • "Hotels in New York"
                • "Hotels in Kerala"
                • "Hotels in Bali"
                • "Find hotels in Marrakech"
                • "Hotels in Zanzibar"
                
                You can search for ANY city in the world! 🌍
            """.trimIndent()
        }

        // Extract dates
        val checkInDate = extractDate(lowerQuery, "check-in") ?: getDefaultDate(7)
        val checkOutDate = extractDate(lowerQuery, "check-out") ?: getDefaultDate(10)

        // Extract number of guests
        val adults = extractGuestCount(lowerQuery)

        Log.d("ChatViewModel", "City Code: $cityCode, Check-in: $checkInDate, Check-out: $checkOutDate, Adults: $adults")
        Log.d("ChatViewModel", "Calling Amadeus Hotels API...")

        return flightHelper.searchHotels(
            cityCode = cityCode,
            checkInDate = checkInDate,
            checkOutDate = checkOutDate,
            adults = adults,
            radius = 20,
            radiusUnit = "KM",
            maxResults = 5
        )
    }


    private fun extractAirline(query: String): String? {
        val airlines = mapOf(
            "delta" to "DL",
            "american" to "AA",
            "american airlines" to "AA",
            "united" to "UA",
            "southwest" to "WN",
            "jetblue" to "B6",
            "alaska" to "AS",
            "spirit" to "NK",
            "frontier" to "F9",
            "lufthansa" to "LH",
            "british airways" to "BA",
            "air france" to "AF",
            "klm" to "KL",
            "emirates" to "EK",
            "qatar" to "QR",
            "cathay pacific" to "CX",
            "singapore airlines" to "SQ",
            "ana" to "NH",
            "japan airlines" to "JL"
        )

        for ((name, code) in airlines) {
            if (query.contains(name)) {
                Log.d("ChatViewModel", "Detected airline: $name ($code)")
                return code
            }
        }
        return null
    }


    private fun extractFlightLocations(query: String): Pair<String?, String?> {
        // Pattern 1: "from X to Y" (most common)
        val fromToPattern = Regex("from\\s+([a-zA-Z][a-zA-Z\\s]{2,})\\s+to\\s+([a-zA-Z][a-zA-Z\\s]{2,})(?:\\s|$)")
        val fromToMatch = fromToPattern.find(query)
        if (fromToMatch != null) {
            val origin = fromToMatch.groupValues[1].trim()
            val destination = fromToMatch.groupValues[2].trim()
            Log.d("ChatViewModel", "Pattern 'from X to Y': origin=$origin, dest=$destination")
            return Pair(origin, destination)
        }

        // Pattern 2: "X to Y" (simpler)
        val toPattern = Regex("([a-zA-Z][a-zA-Z\\s]{2,})\\s+to\\s+([a-zA-Z][a-zA-Z\\s]{2,})(?:\\s|$)")
        val toMatch = toPattern.find(query)
        if (toMatch != null) {
            val origin = toMatch.groupValues[1].trim()
            val destination = toMatch.groupValues[2].trim()

            // Filter out common words
            val excludeWords = listOf("flights", "flight", "fly", "round", "trip", "go", "going")
            if (!excludeWords.contains(origin.lowercase()) && !excludeWords.contains(destination.lowercase())) {
                Log.d("ChatViewModel", "Pattern 'X to Y': origin=$origin, dest=$destination")
                return Pair(origin, destination)
            }
        }

        // Pattern 3: Airport codes "JFK to LAX"
        val codePattern = Regex("\\b([A-Z]{3})\\s+to\\s+([A-Z]{3})\\b")
        val codeMatch = codePattern.find(query.uppercase())
        if (codeMatch != null) {
            val origin = codeMatch.groupValues[1]
            val destination = codeMatch.groupValues[2]
            Log.d("ChatViewModel", "Pattern 'XXX to XXX': origin=$origin, dest=$destination")
            return Pair(origin, destination)
        }

        Log.d("ChatViewModel", "Could not extract flight locations")
        return Pair(null, null)
    }


    private suspend fun getAirportCode(location: String): String? {
        val cleanLocation = location.trim()

        // ✅ IMPROVED: If it's a 3-letter code, check fallback first, then return as-is
        if (cleanLocation.length == 3) {
            val fallbackCode = getFallbackAirportCode(cleanLocation)
            if (fallbackCode != null) {
                Log.d("ChatViewModel", "3-letter code $cleanLocation found in fallback: $fallbackCode")
                return fallbackCode
            }

            // If not in fallback, assume it's already a valid code
            val upperCode = cleanLocation.uppercase()
            Log.d("ChatViewModel", "$cleanLocation appears to be an airport code, using: $upperCode")
            return upperCode
        }

        // Try fallback mapping first (faster and more reliable)
        val fallbackCode = getFallbackAirportCode(cleanLocation)
        if (fallbackCode != null) {
            Log.d("ChatViewModel", "Found airport code via fallback: $fallbackCode for $cleanLocation")
            return fallbackCode
        }

        // Try API search as last resort
        Log.d("ChatViewModel", "Searching API for airport code: $cleanLocation")
        val airportCode = flightHelper.searchAirportCode(cleanLocation)

        if (airportCode != null) {
            Log.d("ChatViewModel", "Found airport code via API: $airportCode for $cleanLocation")
            return airportCode
        }

        Log.d("ChatViewModel", "Could not find airport code for: $cleanLocation")
        return null
    }


    private fun getFallbackAirportCode(location: String): String? {
        val cleanLocation = location.trim().lowercase()

        @Suppress("SpellCheckingInspection")
        val fallbackMap = mapOf(
            // ==================== NORTH AMERICA ====================

            // United States - Major Cities
            "new york" to "NYC", "los angeles" to "LAX", "chicago" to "ORD", "houston" to "IAH",
            "phoenix" to "PHX", "philadelphia" to "PHL", "san antonio" to "SAT", "san diego" to "SAN",
            "dallas" to "DFW", "san jose" to "SJC", "austin" to "AUS", "jacksonville" to "JAX",
            "fort worth" to "DFW", "columbus" to "CMH", "charlotte" to "CLT", "san francisco" to "SFO",
            "indianapolis" to "IND", "seattle" to "SEA", "denver" to "DEN", "washington" to "DCA",
            "boston" to "BOS", "nashville" to "BNA", "el paso" to "ELP", "detroit" to "DTW",
            "portland" to "PDX", "las vegas" to "LAS", "memphis" to "MEM", "louisville" to "SDF",
            "baltimore" to "BWI", "milwaukee" to "MKE", "albuquerque" to "ABQ", "tucson" to "TUS",
            "fresno" to "FAT", "sacramento" to "SMF", "kansas city" to "MCI", "mesa" to "PHX",
            "atlanta" to "ATL", "virginia beach" to "ORF", "omaha" to "OMA", "colorado springs" to "COS",
            "raleigh" to "RDU", "miami" to "MIA", "oakland" to "OAK", "minneapolis" to "MSP",
            "tulsa" to "TUL", "cleveland" to "CLE", "wichita" to "ICT", "new orleans" to "MSY",
            "arlington" to "DFW", "tampa" to "TPA", "orlando" to "MCO", "salt lake city" to "SLC",
            "honolulu" to "HNL", "anchorage" to "ANC", "pittsburgh" to "PIT", "cincinnati" to "CVG",
            "saint louis" to "STL", "st louis" to "STL", "buffalo" to "BUF", "reno" to "RNO",
            "boise" to "BOI", "spokane" to "GEG", "des moines" to "DSM", "rochester" to "ROC",
            "charleston" to "CHS", "savannah" to "SAV", "fort lauderdale" to "FLL", "west palm beach" to "PBI",
            "key west" to "EYW",

            // US Territories
            "san juan" to "SJU", "ponce" to "PSE",

            // Canada
            "toronto" to "YYZ", "montreal" to "YUL", "vancouver" to "YVR", "calgary" to "YYC",
            "edmonton" to "YEG", "ottawa" to "YOW", "winnipeg" to "YWG", "quebec city" to "YQB",
            "halifax" to "YHZ", "victoria" to "YYJ",

            // Mexico
            "mexico city" to "MEX", "guadalajara" to "GDL", "monterrey" to "MTY", "cancun" to "CUN",
            "tijuana" to "TIJ", "puerto vallarta" to "PVR", "cabo san lucas" to "SJD", "los cabos" to "SJD",
            "playa del carmen" to "CUN", "cozumel" to "CZM", "merida" to "MID", "acapulco" to "ACA",
            "mazatlan" to "MZT",

            // ==================== CENTRAL AMERICA & CARIBBEAN ====================

            "havana" to "HAV", "kingston" to "KIN", "montego bay" to "MBJ", "nassau" to "NAS",
            "punta cana" to "PUJ", "santo domingo" to "SDQ", "panama city" to "PTY",
            "guatemala city" to "GUA", "belize city" to "BZE", "san salvador" to "SAL",
            "tegucigalpa" to "TGU", "managua" to "MGA", "port au prince" to "PAP",
            "bridgetown" to "BGI", "port of spain" to "POS", "aruba" to "AUA", "curacao" to "CUR",
            "st maarten" to "SXM", "st thomas" to "STT", "grand cayman" to "GCM",

            // ==================== SOUTH AMERICA ====================

            "sao paulo" to "GRU", "rio de janeiro" to "GIG", "brasilia" to "BSB", "salvador" to "SSA",
            "fortaleza" to "FOR", "belo horizonte" to "CNF", "manaus" to "MAO", "curitiba" to "CWB",
            "recife" to "REC", "porto alegre" to "POA", "buenos aires" to "EZE", "cordoba" to "COR",
            "mendoza" to "MDZ", "rosario" to "ROS", "lima" to "LIM", "cusco" to "CUZ",
            "arequipa" to "AQP", "bogota" to "BOG", "medellin" to "MDE", "cali" to "CLO",
            "cartagena" to "CTG", "santiago" to "SCL", "valparaiso" to "SCL", "quito" to "UIO",
            "guayaquil" to "GYE", "caracas" to "CCS", "montevideo" to "MVD", "la paz" to "LPB",
            "santa cruz" to "VVI", "asuncion" to "ASU", "georgetown" to "GEO", "paramaribo" to "PBM",
            "cayenne" to "CAY",

            // ==================== EUROPE ====================

            // UK & Ireland
            "london" to "LON", "manchester" to "MAN", "birmingham" to "BHX", "glasgow" to "GLA",
            "edinburgh" to "EDI", "liverpool" to "LPL", "bristol" to "BRS", "newcastle" to "NCL",
            "belfast" to "BFS", "dublin" to "DUB", "cork" to "ORK", "shannon" to "SNN",

            // France
            "paris" to "CDG", "nice" to "NCE", "lyon" to "LYS", "marseille" to "MRS",
            "toulouse" to "TLS", "bordeaux" to "BOD", "nantes" to "NTE", "strasbourg" to "SXB",

            // Germany
            "berlin" to "BER", "munich" to "MUC", "frankfurt" to "FRA", "hamburg" to "HAM",
            "cologne" to "CGN", "dusseldorf" to "DUS", "stuttgart" to "STR", "dortmund" to "DTM",
            "nuremberg" to "NUE", "hannover" to "HAJ", "leipzig" to "LEJ", "dresden" to "DRS",

            // Spain
            "madrid" to "MAD", "barcelona" to "BCN", "valencia" to "VLC", "seville" to "SVQ",
            "malaga" to "AGP", "palma" to "PMI", "bilbao" to "BIO", "alicante" to "ALC",
            "granada" to "GRX", "ibiza" to "IBZ",

            // Italy
            "rome" to "FCO", "milan" to "MXP", "venice" to "VCE", "florence" to "FLR",
            "naples" to "NAP", "turin" to "TRN", "bologna" to "BLQ", "pisa" to "PSA",
            "palermo" to "PMO", "catania" to "CTA", "bari" to "BRI", "verona" to "VRN",

            // Netherlands & Belgium
            "amsterdam" to "AMS", "rotterdam" to "RTM", "eindhoven" to "EIN", "brussels" to "BRU",
            "antwerp" to "ANR",

            // Switzerland & Austria
            "zurich" to "ZRH", "geneva" to "GVA", "basel" to "BSL", "bern" to "BRN",
            "vienna" to "VIE", "salzburg" to "SZG", "innsbruck" to "INN", "graz" to "GRZ",

            // Scandinavia
            "stockholm" to "ARN", "copenhagen" to "CPH", "oslo" to "OSL", "helsinki" to "HEL",
            "bergen" to "BGO", "gothenburg" to "GOT", "reykjavik" to "KEF",

            // Eastern Europe
            "warsaw" to "WAW", "krakow" to "KRK", "prague" to "PRG", "budapest" to "BUD",
            "bucharest" to "OTP", "sofia" to "SOF", "athens" to "ATH", "thessaloniki" to "SKG",
            "istanbul" to "IST", "ankara" to "ESB", "izmir" to "ADB", "antalya" to "AYT",
            "moscow" to "SVO", "saint petersburg" to "LED", "st petersburg" to "LED",
            "kiev" to "KBP", "kyiv" to "KBP", "minsk" to "MSQ", "riga" to "RIX",
            "tallinn" to "TLL", "vilnius" to "VNO", "zagreb" to "ZAG", "belgrade" to "BEG",
            "bratislava" to "BTS", "ljubljana" to "LJU",

            // Portugal
            "lisbon" to "LIS", "porto" to "OPO", "faro" to "FAO", "funchal" to "FNC",

            // Greece & Cyprus
            "santorini" to "JTR", "mykonos" to "JMK", "crete" to "HER", "rhodes" to "RHO",
            "corfu" to "CFU", "nicosia" to "NIC", "larnaca" to "LCA", "paphos" to "PFO",

            // Malta
            "malta" to "MLA", "valletta" to "MLA",

            // ==================== MIDDLE EAST ====================

            "dubai" to "DXB", "abu dhabi" to "AUH", "sharjah" to "SHJ", "doha" to "DOH",
            "riyadh" to "RUH", "jeddah" to "JED", "medina" to "MED", "dammam" to "DMM",
            "muscat" to "MCT", "kuwait city" to "KWI", "manama" to "BAH", "tel aviv" to "TLV",
            "jerusalem" to "TLV", "amman" to "AMM", "beirut" to "BEY", "damascus" to "DAM",
            "baghdad" to "BGW", "erbil" to "EBL", "tehran" to "IKA", "shiraz" to "SYZ",
            "isfahan" to "IFN", "sanaa" to "SAH", "aden" to "ADE",

            // ==================== AFRICA ====================

            // North Africa
            "cairo" to "CAI", "alexandria" to "ALY", "luxor" to "LXR", "sharm el sheikh" to "SSH",
            "hurghada" to "HRG", "casablanca" to "CMN", "marrakech" to "RAK", "rabat" to "RBA",
            "fez" to "FEZ", "tangier" to "TNG", "algiers" to "ALG", "oran" to "ORN",
            "tunis" to "TUN", "tripoli" to "TIP", "benghazi" to "BEN",

            // East Africa
            "nairobi" to "NBO", "mombasa" to "MBA", "dar es salaam" to "DAR", "zanzibar" to "ZNZ",
            "kilimanjaro" to "JRO", "addis ababa" to "ADD", "kigali" to "KGL", "entebbe" to "EBB",
            "kampala" to "EBB", "mogadishu" to "MGQ", "djibouti" to "JIB",

            // Southern Africa
            "johannesburg" to "JNB", "cape town" to "CPT", "durban" to "DUR", "pretoria" to "PRY",
            "port elizabeth" to "PLZ", "windhoek" to "WDH", "gaborone" to "GBE", "lusaka" to "LUN",
            "harare" to "HRE", "bulawayo" to "BUQ", "maputo" to "MPM", "antananarivo" to "TNR",
            "mauritius" to "MRU", "seychelles" to "SEZ", "reunion" to "RUN",

            // West Africa
            "lagos" to "LOS", "abuja" to "ABV", "kano" to "KAN", "accra" to "ACC",
            "kumasi" to "KMS", "abidjan" to "ABJ", "dakar" to "DSS", "bamako" to "BKO",
            "conakry" to "CKY", "freetown" to "FNA", "monrovia" to "MLW", "ouagadougou" to "OUA",
            "niamey" to "NIM", "lome" to "LFW", "cotonou" to "COO",

            // ==================== ASIA ====================

            // East Asia - Japan
            "tokyo" to "TYO", "osaka" to "OSA", "kyoto" to "ITM", "nagoya" to "NGO",
            "sapporo" to "CTS", "fukuoka" to "FUK", "okinawa" to "OKA", "hiroshima" to "HIJ",
            "sendai" to "SDJ", "kobe" to "UKB",

            // East Asia - South Korea
            "seoul" to "ICN", "busan" to "PUS", "jeju" to "CJU", "daegu" to "TAE",
            "gwangju" to "KWJ",

            // East Asia - China
            "beijing" to "PEK", "shanghai" to "PVG", "guangzhou" to "CAN", "shenzhen" to "SZX",
            "chengdu" to "CTU", "hangzhou" to "HGH", "xi'an" to "XIY", "xian" to "XIY",
            "chongqing" to "CKG", "tianjin" to "TSN", "wuhan" to "WUH", "nanjing" to "NKG",
            "shenyang" to "SHE", "dalian" to "DLC", "qingdao" to "TAO", "kunming" to "KMG",
            "xiamen" to "XMN", "harbin" to "HRB", "macau" to "MFM",

            // East Asia - Taiwan & Hong Kong
            "taipei" to "TPE", "kaohsiung" to "KHH", "taichung" to "RMQ", "hong kong" to "HKG",

            // East Asia - Mongolia
            "ulaanbaatar" to "ULN",

            // Southeast Asia
            "singapore" to "SIN", "bangkok" to "BKK", "phuket" to "HKT", "chiang mai" to "CNX",
            "pattaya" to "UTP", "krabi" to "KBV", "kuala lumpur" to "KUL", "penang" to "PEN",
            "langkawi" to "LGK", "kota kinabalu" to "BKI", "jakarta" to "CGK", "bali" to "DPS",
            "surabaya" to "SUB", "yogyakarta" to "JOG", "bandung" to "BDO", "medan" to "KNO",
            "manila" to "MNL", "cebu" to "CEB", "davao" to "DVO", "boracay" to "MPH",
            "hanoi" to "HAN", "ho chi minh" to "SGN", "da nang" to "DAD", "nha trang" to "CXR",
            "phnom penh" to "PNH", "siem reap" to "REP", "vientiane" to "VTE", "luang prabang" to "LPQ",
            "yangon" to "RGN", "mandalay" to "MDL", "bandar seri begawan" to "BWN", "dili" to "DIL",

            // South Asia - India
            "delhi" to "DEL", "mumbai" to "BOM", "bangalore" to "BLR", "bengaluru" to "BLR",
            "chennai" to "MAA", "hyderabad" to "HYD", "kolkata" to "CCU", "pune" to "PNQ",
            "ahmedabad" to "AMD", "jaipur" to "JAI", "lucknow" to "LKO", "chandigarh" to "IXC",
            "kochi" to "COK", "cochin" to "COK", "thiruvananthapuram" to "TRV", "trivandrum" to "TRV",
            "goa" to "GOI", "calicut" to "CCJ", "kozhikode" to "CCJ", "coimbatore" to "CJB",
            "indore" to "IDR", "bhopal" to "BHO", "patna" to "PAT", "vadodara" to "BDQ",
            "surat" to "STV", "vishakhapatnam" to "VTZ", "nagpur" to "NAG", "varanasi" to "VNS",
            "amritsar" to "ATQ", "srinagar" to "SXR", "guwahati" to "GAU", "imphal" to "IMF",
            "agartala" to "IXA", "bagdogra" to "IXB", "port blair" to "IXZ", "leh" to "IXL",

            // South Asia - Pakistan
            "karachi" to "KHI", "lahore" to "LHE", "islamabad" to "ISB", "faisalabad" to "LYP",
            "peshawar" to "PEW", "quetta" to "UET", "multan" to "MUX",

            // South Asia - Bangladesh
            "dhaka" to "DAC", "chittagong" to "CGP", "sylhet" to "ZYL",

            // South Asia - Sri Lanka
            "colombo" to "CMB", "kandy" to "KDZ", "galle" to "KDZ",

            // South Asia - Nepal & Bhutan
            "kathmandu" to "KTM", "pokhara" to "PKR", "paro" to "PBH", "thimphu" to "PBH",

            // South Asia - Maldives
            "male" to "MLE", "maldives" to "MLE",

            // Central Asia
            "tashkent" to "TAS", "samarkand" to "SKD", "almaty" to "ALA", "astana" to "TSE",
            "bishkek" to "FRU", "dushanbe" to "DYU", "ashgabat" to "ASB",

            // ==================== OCEANIA ====================

            // Australia
            "sydney" to "SYD", "melbourne" to "MEL", "brisbane" to "BNE", "perth" to "PER",
            "adelaide" to "ADL", "gold coast" to "OOL", "canberra" to "CBR", "hobart" to "HBA",
            "darwin" to "DRW", "cairns" to "CNS", "townsville" to "TSV", "launceston" to "LST",
            "sunshine coast" to "MCY", "ballina" to "BNK", "albury" to "ABX",
            "ayers rock" to "AYQ", "uluru" to "AYQ",

            "auckland" to "AKL", "wellington" to "WLG", "christchurch" to "CHC", "queenstown" to "ZQN",
            "dunedin" to "DUD", "rotorua" to "ROT", "hamilton" to "HLZ", "napier" to "NPE",
            "palmerston north" to "PMR", "nelson" to "NSN",

            "fiji" to "NAN", "suva" to "SUV", "nadi" to "NAN", "port moresby" to "POM",
            "noumea" to "NOU", "papeete" to "PPT", "tahiti" to "PPT", "apia" to "APW",
            "nuku'alofa" to "TBU", "port vila" to "VLI", "rarotonga" to "RAR", "honiara" to "HIR",
            "guam" to "GUM", "saipan" to "SPN", "pohnpei" to "PNI", "majuro" to "MAJ",
            "palau" to "ROR", "koror" to "ROR",

            "jfk" to "JFK", "lga" to "LGA", "ewr" to "EWR", "lax" to "LAX", "ord" to "ORD",
            "dfw" to "DFW", "atl" to "ATL", "mia" to "MIA", "sfo" to "SFO", "phx" to "PHX",
            "iah" to "IAH", "sea" to "SEA", "las" to "LAS", "mco" to "MCO", "bos" to "BOS",
            "dtw" to "DTW", "msp" to "MSP", "phl" to "PHL", "den" to "DEN", "slc" to "SLC",
            "bwi" to "BWI", "dca" to "DCA", "hnl" to "HNL",

            "lhr" to "LHR", "lgw" to "LGW", "cdg" to "CDG", "fra" to "FRA", "ams" to "AMS",
            "mad" to "MAD", "bcn" to "BCN", "fco" to "FCO", "mxp" to "MXP", "muc" to "MUC",
            "dxb" to "DXB", "sin" to "SIN", "hkg" to "HKG", "nrt" to "NRT", "hnd" to "HND",
            "kix" to "KIX", "icn" to "ICN", "pvg" to "PVG", "pek" to "PEK", "bkk" to "BKK",
            "syd" to "SYD", "mel" to "MEL", "akl" to "AKL", "yyz" to "YYZ", "yvr" to "YVR",
            "gru" to "GRU", "gig" to "GIG", "eze" to "EZE", "scl" to "SCL", "bog" to "BOG",
            "lim" to "LIM", "mex" to "MEX", "can" to "CAN", "del" to "DEL", "bom" to "BOM",
            "blr" to "BLR", "ist" to "IST", "doh" to "DOH", "auh" to "AUH", "jnb" to "JNB",
            "cpt" to "CPT", "cai" to "CAI", "cmn" to "CMN", "nbo" to "NBO"
        )

        return fallbackMap[cleanLocation]
    }


    private suspend fun extractCityCode(query: String): String? {

        val cityName = extractCityName(query)

        if (cityName == null) {
            Log.d("ChatViewModel", "Could not extract city name from query")
            return null
        }

        Log.d("ChatViewModel", "Extracted city name: $cityName")


        val fallbackCode = getFallbackAirportCode(cityName)
        if (fallbackCode != null) {
            Log.d("ChatViewModel", "Found city code via fallback: $fallbackCode for $cityName")
            return fallbackCode
        }

        val cityCode = flightHelper.searchCityCode(cityName)

        if (cityCode != null) {
            Log.d("ChatViewModel", "Found city code via API: $cityCode for $cityName")
            return cityCode
        } else {
            Log.d("ChatViewModel", "Could not find city code for: $cityName")
            return null
        }
    }


    private fun extractCityName(query: String): String? {

        val patterns = listOf(
            // "in [city]"
            Regex("(?:in|at)\\s+([a-zA-Z][a-zA-Z\\s]{2,})(?:\\s+(?:on|from|for|to|december|january|february|march|april|may|june|july|august|september|october|november|\\d+|$))"),
            // "hotels [city]"
            Regex("hotels?\\s+(?:in|at)?\\s*([a-zA-Z][a-zA-Z\\s]{2,})(?:\\s|$)"),
            // "find hotels [city]"
            Regex("find\\s+hotels?\\s+(?:in|at)?\\s*([a-zA-Z][a-zA-Z\\s]{2,})(?:\\s|$)"),
            // Just city name at the end
            Regex("\\s+([a-zA-Z][a-zA-Z\\s]{2,})$")
        )

        for (pattern in patterns) {
            val match = pattern.find(query)
            if (match != null) {
                val city = match.groupValues[1].trim()
                // Filter out common words that aren't cities
                val excludeWords = listOf("hotels", "hotel", "find", "search", "book", "show", "me", "the", "a", "an")
                if (city.isNotEmpty() &&
                    city.length > 2 &&
                    !excludeWords.contains(city.lowercase())) {
                    Log.d("ChatViewModel", "Detected city name: $city")
                    return city
                }
            }
        }

        return null
    }


    private fun extractGuestCount(query: String): Int {

        val patterns = listOf(
            Regex("(\\d+)\\s+(adult|adults|people|person|guest|guests)"),
            Regex("for\\s+(\\d+)")
        )

        for (pattern in patterns) {
            val match = pattern.find(query)
            if (match != null) {
                val count = match.groupValues[1].toIntOrNull()
                if (count != null && count in 1..9) {
                    Log.d("ChatViewModel", "Detected guest count: $count")
                    return count
                }
            }
        }

        return 2
    }

    private fun extractDate(query: String, context: String = "departure"): String? {

        val datePattern = Regex("(\\d{4})-(\\d{2})-(\\d{2})")
        val match = datePattern.find(query)
        if (match != null) {
            Log.d("ChatViewModel", "Found explicit date: ${match.value}")
            return match.value
        }


        val calendar = Calendar.getInstance()

        when {
            query.contains("today") -> {
                Log.d("ChatViewModel", "Detected: today")
            }
            query.contains("tomorrow") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                Log.d("ChatViewModel", "Detected: tomorrow")
            }
            query.contains("next week") -> {
                calendar.add(Calendar.DAY_OF_YEAR, 7)
                Log.d("ChatViewModel", "Detected: next week")
            }
            query.contains("next month") -> {
                calendar.add(Calendar.MONTH, 1)
                Log.d("ChatViewModel", "Detected: next month")
            }
        }

        val monthDayPattern = Regex("(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})", RegexOption.IGNORE_CASE)
        val monthMatch = monthDayPattern.find(query)
        if (monthMatch != null) {
            val month = monthMatch.groupValues[1].lowercase()
            val day = monthMatch.groupValues[2].toInt()

            val monthNum = when (month) {
                "january" -> 0
                "february" -> 1
                "march" -> 2
                "april" -> 3
                "may" -> 4
                "june" -> 5
                "july" -> 6
                "august" -> 7
                "september" -> 8
                "october" -> 9
                "november" -> 10
                "december" -> 11
                else -> calendar.get(Calendar.MONTH)
            }

            calendar.set(Calendar.MONTH, monthNum)
            calendar.set(Calendar.DAY_OF_MONTH, day)


            if (calendar.before(Calendar.getInstance())) {
                calendar.add(Calendar.YEAR, 1)
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedDate = dateFormat.format(calendar.time)
            Log.d("ChatViewModel", "Detected month/day: $month $day -> $formattedDate")
            return formattedDate
        }

        return null
    }


    private fun getDefaultDate(daysFromNow: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, daysFromNow)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(calendar.time)
    }


    private fun getAIResponse(userMessage: String, detectedIntent: IntentDetector.DetectedIntent) {
        _isTyping.value = true

        viewModelScope.launch {
            try {
                val enhancedPrompt = buildEnhancedPrompt(userMessage, detectedIntent)
                val botResponse = travelAgent.processMessage(enhancedPrompt)

                val cleanedMessage = botResponse.copy(
                    text = cleanMarkdownFormatting(botResponse.text)
                )

                addBotMessage(cleanedMessage)
            } catch (e: Exception) {
                handleError(e)
            } finally {
                _isTyping.value = false
            }
        }
    }


    private fun buildEnhancedPrompt(userMessage: String, detectedIntent: IntentDetector.DetectedIntent): String {
        return when (detectedIntent.intent) {
            IntentDetector.Intent.EXPENSE_TRACKER -> {
                "You are a travel expense assistant. User asked: '$userMessage'. Provide brief helpful tips."
            }
            IntentDetector.Intent.ITINERARY -> {
                "You are a travel itinerary assistant. User asked: '$userMessage'. Provide brief helpful tips."
            }
            IntentDetector.Intent.CHECKLIST -> {
                "You are a packing assistant. User asked: '$userMessage'. Provide brief helpful tips."
            }
            else -> userMessage
        }
    }


    private fun cleanMarkdownFormatting(text: String): String {
        return text
            .replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            .replace("```", "")
            .replace(Regex("^#{1,6}\\s"), "")
            .replace(Regex("\\n#{1,6}\\s"), "\n")
            .trim()
    }


    private fun handleError(e: Exception) {
        Log.e("ChatViewModel", "Error occurred", e)
        val errorMessage = Message(
            id = System.currentTimeMillis(),
            text = "I'm having trouble processing your request. " +
                    "Error: ${e.message}\n\n" +
                    "Please check your internet connection and try again.",
            sender = MessageSender.BOT,
            timestamp = Date(),
            type = MessageType.GENERAL
        )
        addBotMessage(errorMessage)
    }


    private fun addMessage(message: Message) {
        val currentMessages = _messages.value ?: emptyList()
        _messages.value = currentMessages + message
    }


    private fun addBotMessage(message: Message) {
        val currentMessages = _messages.value.orEmpty().toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages

        viewModelScope.launch {
            val messageToSave = message.copy(actionOptions = null)
            chatRepository.saveMessage(messageToSave)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            try {
                chatRepository.clearHistory()
                _messages.value = emptyList()
                travelAgent.clearHistory()
                addWelcomeMessage()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error clearing chat", e)
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
            val currentMessages = _messages.value ?: emptyList()
            _messages.value = currentMessages.filter { it.id != messageId }
        }
    }
}
