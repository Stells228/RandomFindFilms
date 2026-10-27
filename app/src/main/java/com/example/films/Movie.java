package com.example.films;

public class Movie {
    private String title;     // название
    private int year;         // год выпуска
    private String director;  // режиссёр
    private String genre;     // жанр
    private int duration;     // длительность в минутах
    private String country;   // страна
    private String poster;    // имя файла постера

    public String getTitle() { return title; }
    public int getYear() { return year; }
    public String getDirector() { return director; }
    public String getGenre() { return genre; }
    public int getDuration() { return duration; }
    public String getCountry() { return country; }
    public String getPoster() { return poster; }
}
