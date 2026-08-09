package com.miguelaetxio.mimoo.data.remote

/**
 * H15 (miMooutCast), S032 -- recopilatorio fijo de las obras clásicas
 * más conocidas de todos los tiempos, orden explícito de Miguel
 * Ángel: *"clásica no es necesario buscar con tanto subgénero,
 * buscamos classical y punto. Coges un recopilatorio de los mejores
 * 100 temas de clásica de todos los tiempos y vamos poniendo temas
 * aleatoriamente sin repetir de ese recopilatorio hasta encontrar
 * temas."* Sustituye por completo, para el ancla clásica, la búsqueda
 * dinámica contra MusicBrainz (`suggestWorkForGenre()`/
 * `suggestRelatedArtist()`+`suggestWorkForArtist()`) -- esas seguían
 * encontrando obras demasiado marginales para tener garantías de
 * verificación rápida en YouTube/MusicBrainz/Discogs/Wikidata. Cien
 * obras de compositor+título real, elegidas por ser de las más
 * grabadas y conocidas del repertorio clásico -- máxima probabilidad
 * de encontrar una grabación real y verificable rápido.
 *
 * Compositor y título únicamente -- ni letra, ni partitura, ni texto
 * de la obra en sí, solo el nombre para buscar. `PlayerManager` baraja
 * esta lista una vez por sesión y la recorre en ese orden sin repetir,
 * ver `classicalHitsOrder`/`classicalHitsIndex`.
 */
object ClassicalGreatestHits {
    val works: List<Pair<String, String>> = listOf(
        "Ludwig van Beethoven" to "Symphony No. 5",
        "Ludwig van Beethoven" to "Symphony No. 9",
        "Ludwig van Beethoven" to "Moonlight Sonata",
        "Ludwig van Beethoven" to "Für Elise",
        "Ludwig van Beethoven" to "Symphony No. 6 Pastoral",
        "Ludwig van Beethoven" to "Piano Concerto No. 5 Emperor",
        "Wolfgang Amadeus Mozart" to "Eine kleine Nachtmusik",
        "Wolfgang Amadeus Mozart" to "Requiem",
        "Wolfgang Amadeus Mozart" to "The Magic Flute Overture",
        "Wolfgang Amadeus Mozart" to "Symphony No. 40",
        "Wolfgang Amadeus Mozart" to "Piano Sonata No. 11 Alla Turca",
        "Wolfgang Amadeus Mozart" to "The Marriage of Figaro Overture",
        "Johann Sebastian Bach" to "Air on the G String",
        "Johann Sebastian Bach" to "Toccata and Fugue in D minor",
        "Johann Sebastian Bach" to "Brandenburg Concerto No. 3",
        "Johann Sebastian Bach" to "Jesu Joy of Man's Desiring",
        "Johann Sebastian Bach" to "Cello Suite No. 1 Prelude",
        "Johann Sebastian Bach" to "Goldberg Variations Aria",
        "Antonio Vivaldi" to "The Four Seasons Spring",
        "Antonio Vivaldi" to "The Four Seasons Winter",
        "Frédéric Chopin" to "Nocturne Op. 9 No. 2",
        "Frédéric Chopin" to "Fantaisie-Impromptu",
        "Frédéric Chopin" to "Revolutionary Etude",
        "Frédéric Chopin" to "Minute Waltz",
        "Pyotr Ilyich Tchaikovsky" to "Swan Lake",
        "Pyotr Ilyich Tchaikovsky" to "The Nutcracker Dance of the Sugar Plum Fairy",
        "Pyotr Ilyich Tchaikovsky" to "1812 Overture",
        "Pyotr Ilyich Tchaikovsky" to "Piano Concerto No. 1",
        "Pyotr Ilyich Tchaikovsky" to "Symphony No. 6 Pathetique",
        "Johannes Brahms" to "Hungarian Dance No. 5",
        "Johannes Brahms" to "Lullaby Wiegenlied",
        "Johannes Brahms" to "Symphony No. 3 Third Movement",
        "Richard Wagner" to "Ride of the Valkyries",
        "Richard Wagner" to "Bridal Chorus",
        "Giuseppe Verdi" to "La donna e mobile",
        "Giuseppe Verdi" to "Va pensiero",
        "Giacomo Puccini" to "Nessun dorma",
        "Giacomo Puccini" to "O mio babbino caro",
        "Georges Bizet" to "Habanera Carmen",
        "Georges Bizet" to "Toreador Song",
        "Edvard Grieg" to "In the Hall of the Mountain King",
        "Edvard Grieg" to "Morning Mood",
        "Camille Saint-Saens" to "The Swan",
        "Camille Saint-Saens" to "Danse Macabre",
        "Gustav Holst" to "Jupiter The Planets",
        "Gustav Holst" to "Mars The Planets",
        "Claude Debussy" to "Clair de Lune",
        "Claude Debussy" to "Prelude to the Afternoon of a Faun",
        "Maurice Ravel" to "Bolero",
        "Maurice Ravel" to "Pavane pour une infante defunte",
        "Franz Schubert" to "Ave Maria",
        "Franz Schubert" to "Symphony No. 8 Unfinished",
        "Franz Liszt" to "Liebestraum",
        "Franz Liszt" to "Hungarian Rhapsody No. 2",
        "Johann Pachelbel" to "Canon in D",
        "George Frideric Handel" to "Hallelujah Chorus Messiah",
        "George Frideric Handel" to "Water Music",
        "Antonin Dvorak" to "Symphony No. 9 From the New World",
        "Antonin Dvorak" to "Humoresque",
        "Gustav Mahler" to "Symphony No. 5 Adagietto",
        "Sergei Rachmaninoff" to "Piano Concerto No. 2",
        "Sergei Rachmaninoff" to "Rhapsody on a Theme of Paganini",
        "Sergei Prokofiev" to "Dance of the Knights Romeo and Juliet",
        "Sergei Prokofiev" to "Peter and the Wolf",
        "Igor Stravinsky" to "The Rite of Spring",
        "Igor Stravinsky" to "Firebird Suite",
        "Carl Orff" to "O Fortuna Carmina Burana",
        "Edward Elgar" to "Pomp and Circumstance",
        "Edward Elgar" to "Nimrod Enigma Variations",
        "Jean Sibelius" to "Finlandia",
        "Modest Mussorgsky" to "Night on Bald Mountain",
        "Modest Mussorgsky" to "Pictures at an Exhibition Promenade",
        "Nikolai Rimsky-Korsakov" to "Flight of the Bumblebee",
        "Nikolai Rimsky-Korsakov" to "Scheherazade",
        "Aaron Copland" to "Fanfare for the Common Man",
        "Aaron Copland" to "Appalachian Spring",
        "Samuel Barber" to "Adagio for Strings",
        "Ottorino Respighi" to "Pines of Rome",
        "Manuel de Falla" to "Ritual Fire Dance",
        "Astor Piazzolla" to "Libertango",
        "Joaquin Rodrigo" to "Concierto de Aranjuez",
        "Antonio Salieri" to "Symphony in D major Veneziana",
        "Domenico Scarlatti" to "Sonata in D minor K. 141",
        "Henry Purcell" to "Dido's Lament",
        "Felix Mendelssohn" to "Wedding March",
        "Felix Mendelssohn" to "Violin Concerto in E minor",
        "Felix Mendelssohn" to "A Midsummer Night's Dream Overture",
        "Robert Schumann" to "Traumerei",
        "Franz Joseph Haydn" to "Symphony No. 94 Surprise",
        "Franz Joseph Haydn" to "Trumpet Concerto",
        "Ludwig van Beethoven" to "Symphony No. 7 Second Movement",
        "Wolfgang Amadeus Mozart" to "Clarinet Concerto",
        "Johann Sebastian Bach" to "Concerto for Two Violins",
        "Frédéric Chopin" to "Ballade No. 1",
        "Pyotr Ilyich Tchaikovsky" to "Violin Concerto in D major",
        "Antonio Vivaldi" to "The Four Seasons Summer",
        "Gioachino Rossini" to "William Tell Overture",
        "Gioachino Rossini" to "The Barber of Seville Overture",
        "Charles Gounod" to "Ave Maria",
        "Leo Delibes" to "Flower Duet Lakme",
        "Zoltan Kodaly" to "Dances of Galanta",
    )
}
