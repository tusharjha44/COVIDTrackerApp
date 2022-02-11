package com.example.covidtrackerapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.example.covidtrackerapp.databinding.ActivityMainBinding
import com.google.gson.GsonBuilder
import com.robinhood.ticker.TickerUtils
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
        const val BASE_URL = "https://covidtracking.com/api/v1/"
        const val ALL_STATES = "All (Nationwide)"
    }

    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var binding: ActivityMainBinding
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationDailyData: List<CovidData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = getString(R.string.app_discription)

        //GSON object
        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        val covidApiService = retrofit.create(CovidApiService::class.java)

        //Fetch National Data
        covidApiService.getNationalData().enqueue(object : Callback<List<CovidData>>{
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.i(TAG, "onResponse $response")
                val nationalData = response.body()
                if (nationalData == null) {
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }

                setUpEventListeners()

                nationDailyData = nationalData.reversed()
                Log.i(TAG, "Update graph with national data")
                updateDisplayWithData(nationDailyData)

            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }
        })

        //Fetch State Data
        covidApiService.getStatesData().enqueue(object : Callback<List<CovidData>>{
            override fun onResponse(
                call: Call<List<CovidData>>,
                response: Response<List<CovidData>>
            ) {
                Log.i(TAG, "onResponse $response")
                val stateData = response.body()
                if (stateData == null) {
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }

                perStateDailyData = stateData.reversed().groupBy {
                    it.state
                }
                Log.i(TAG, "Update spinner with state names")

                //Update spinner with state names
                updateSpinnerWithStateDat(perStateDailyData.keys)

            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }
        })

    }

    private fun updateSpinnerWithStateDat(stateNames: Set<String>) {
        val stateAbbreviationList = stateNames.toMutableList()
        stateAbbreviationList.sort()

        stateAbbreviationList.add(0, ALL_STATES)

        //Add state list as data source to spinner
        val spinner = binding.spinnerSelect
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item, stateAbbreviationList
        )
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener,
            AdapterView.OnItemClickListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val selectedState = stateAbbreviationList[position]
                val selectedData = perStateDailyData[selectedState]
                if (selectedData != null) {
                    updateDisplayWithData(selectedData)
                }else{
                    updateDisplayWithData(nationDailyData)
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                updateDisplayWithData(nationDailyData)
            }

            override fun onItemClick(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                //Not used
            }
        }


    }


    private fun setUpEventListeners() {

        binding.tickerView.setCharacterLists(TickerUtils.provideNumberList())

        //Add listener for the user scrubbing on the chart
        binding.sparkView.isScrubEnabled = true
        binding.sparkView.setScrubListener {
            if(it is CovidData){
                updateInfoForDate(it)
            }
        }

        //Respond to radio button
        binding.radioGroupTimeSelection.setOnCheckedChangeListener { _, checkedId ->
            adapter.daysAgo = when(checkedId){
                R.id.radioButtonWeek -> TimeScale.WEEK
                R.id.radioButtonMonth -> TimeScale.MONTH
                else -> TimeScale.MAX
            }

            adapter.notifyDataSetChanged()
        }

        binding.tvRadioGroup.setOnCheckedChangeListener{_,checkedId ->
            when(checkedId){
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonNegative -> updateDisplayMetric(Metric.NEGATIVE)
                else -> updateDisplayMetric(Metric.DEATH)
            }
        }

    }

    private fun updateDisplayMetric(metric: Metric) {

        //Update the color of chart
        val colorRes = when(metric){
            Metric.DEATH -> R.color.colorDeath
            Metric.NEGATIVE -> R.color.colorNegative
            else -> R.color.colorPositive
        }
        @ColorInt
        val colorInt = ContextCompat.getColor(this,colorRes)
        binding.sparkView.lineColor = colorInt
        binding.tickerView.textColor = colorInt

        //Update the metric
        adapter.metric = metric
        adapter.notifyDataSetChanged()

        //Reset number and date in the last textViews.
        updateInfoForDate(currentlyShownData.last())

    }

    private fun updateDisplayWithData(dailyData: List<CovidData>) {

        currentlyShownData = dailyData

        //Create a new SparkAdapter with data

        adapter = CovidSparkAdapter(dailyData)

        binding.sparkView.adapter = adapter


        //Update radio button to select the positive cases and max time by default

        binding.radioButtonPositive.isChecked = true
        binding.radioButtonMax.isChecked = true

        //Display metric for the most recent date
        updateDisplayMetric(Metric.POSITIVE)
    }

    private fun updateInfoForDate(covidData: CovidData) {

        val numCases = when(adapter.metric) {
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }

        binding.tickerView.text = NumberFormat.getInstance().format(numCases)

        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        binding.tvDateLabel.text = outputDateFormat.format(covidData.dateChecked)

    }
}