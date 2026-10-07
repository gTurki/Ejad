package com.example.ejadwebapplication.Config;

import com.example.ejadwebapplication.Enums.LocationType;
import com.example.ejadwebapplication.Model.*;
import com.example.ejadwebapplication.Repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// نعبي بيانات تجريبية أول مرة يشتغل فيها المشروع
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final LocationRepository locationRepository;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        // كل جزء يتحقق لحاله، عشان لو الأماكن موجودة والتصنيفات لا، تنضاف التصنيفات
        seedCategories();
        seedLocationsAndAccounts();
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) {
            return;
        }
        categoryRepository.saveAll(List.of(
                createCategory("Electronics", "Phones, laptops, headphones, chargers"),
                createCategory("Wallets", "Wallets and card holders"),
                createCategory("Bags", "Backpacks, handbags, luggage"),
                createCategory("Keys", "House keys and car keys"),
                createCategory("Documents", "IDs, passports, cards and papers"),
                createCategory("Clothes", "Clothes, abayas, shoes, accessories"),
                createCategory("Other", "Anything else")
        ));
    }

    private void seedLocationsAndAccounts() {
        if (locationRepository.count() > 0) {
            return;
        }

        Location airport = createLocation("King Khalid International Airport",
                "Lost and found desk, Terminal 1", "Riyadh", LocationType.AIRPORT);
        Location mall = createLocation("Al-Hamra mall",
                "Customer service desk, ground floor", "Riyadh", LocationType.MALL);
        Location metro = createLocation("KAFD Metro Station",
                "Station security office", "Riyadh", LocationType.METRO);
        Location university = createLocation("King Saud University",
                "Campus security office", "Riyadh", LocationType.UNIVERSITY);
        locationRepository.saveAll(List.of(airport, mall, metro, university));

        Admin admin = fillAccount(new Admin(), "System Admin", "admin",
                "eazmirara+admin@gmail.com", "Admin1234", "0543230737");
        adminRepository.save(admin);

        User sara = fillAccount(new User(), "Sara Alqahtani", "sara",
                "eazmirara+sara@gmail.com", "Sara1234", "0543230737");
        User fahad = fillAccount(new User(), "Fahad Alotaibi", "fahad",
                "eazmirara+fahad@gmail.com", "Fahad1234", "0556544660");
        userRepository.saveAll(List.of(sara, fahad));

        Staff khalid = fillAccount(new Staff(), "Khalid Alharbi", "khalid.staff",
                "eazmirara+khalid.staff@gmail.com", "Khalid1234", "0549931017");
        khalid.setLocation(airport);
        khalid.setIsVerified(true);

        // غير موثّقة عشان تجربون endpoint التوثيق
        Staff noura = fillAccount(new Staff(), "Noura Alshehri", "noura.staff",
                "eazmirara+noura.staff@gmail.com", "Noura1234", "0543230737");
        noura.setLocation(mall);
        noura.setIsVerified(false);

        staffRepository.saveAll(List.of(khalid, noura));
    }

    private Category createCategory(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        return category;
    }

    private Location createLocation(String name, String description, String city, LocationType type) {
        Location location = new Location();
        location.setName(name);
        location.setDescription(description);
        location.setCity(city);
        location.setType(type);
        return location;
    }

    private <T extends BaseAccount> T fillAccount(T account, String fullName, String username,
                                                   String email, String password, String phone) {
        account.setFullName(fullName);
        account.setUsername(username);
        account.setEmail(email);
        account.setPassword(password);
        account.setPhone(phone);
        return account;
    }
}
