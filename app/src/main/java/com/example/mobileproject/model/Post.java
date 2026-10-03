package com.example.mobileproject.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Post {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String HIDDEN = "HIDDEN";

    public static final String DIRECT = "DIRECT";
    public static final String POINTS = "POINTS";

    private String postId;
    private String ownerId;
    private String title;
    private String description;
    private String addressText;
    private double latitude;
    private double longitude;
    private int maxGuests;
    private int bedroomCount;
    private int bathroomCount;
    private String status;
    private String exchangeType;
    private int pointsPerNight;
    private Date availableFrom;
    private Date availableTo;
    private List<String> amenityIds;

    public Post() {
        status = PENDING;
        exchangeType = DIRECT;
        maxGuests = 1;
        amenityIds = new ArrayList<>();
    }

    public Post(String postId, String ownerId, String title,
                String description, String addressText,
                double latitude, double longitude, int maxGuests,
                int bedroomCount, int bathroomCount, String status,
                String exchangeType, int pointsPerNight,
                Date availableFrom, Date availableTo,
                List<String> amenityIds) {
        this();
        this.postId = postId;
        this.ownerId = ownerId;
        this.title = title;
        this.description = description;
        this.addressText = addressText;
        setLatitude(latitude);
        setLongitude(longitude);
        setMaxGuests(maxGuests);
        setBedroomCount(bedroomCount);
        setBathroomCount(bathroomCount);
        setStatus(status);
        setExchangeType(exchangeType);
        setPointsPerNight(pointsPerNight);
        setAvailablePeriod(availableFrom, availableTo);
        setAmenityIds(amenityIds);
    }

    // Cập nhật thông tin trên đối tượng, chưa lưu lên Firebase.
    public void updateInfo(String title, String description, String addressText) {
        this.title = title;
        this.description = description;
        this.addressText = addressText;
    }

    public void setAvailablePeriod(Date from, Date to) {
        if (from == null || to == null || !to.after(from)) {
            throw new IllegalArgumentException(
                    "Ngày kết thúc phải sau ngày bắt đầu."
            );
        }
        availableFrom = new Date(from.getTime());
        availableTo = new Date(to.getTime());
    }

    // Chỉ kiểm tra khoảng thời gian chủ nhà cho phép.
    // Việc kiểm tra trùng lịch trao đổi sẽ xử lý ở phần Exchange.
    public boolean isWithinAvailablePeriod(Date startDate, Date endDate) {
        return startDate != null && endDate != null
                && availableFrom != null && availableTo != null
                && endDate.after(startDate)
                && !startDate.before(availableFrom)
                && !endDate.after(availableTo);
    }

    public long calculatePoints(int numberOfNights) {
        if (numberOfNights <= 0) {
            throw new IllegalArgumentException("Số đêm phải lớn hơn 0.");
        }
        if (!POINTS.equals(exchangeType) || pointsPerNight <= 0) {
            throw new IllegalStateException(
                    "Bài đăng chưa có mức điểm trao đổi hợp lệ."
            );
        }
        return (long) pointsPerNight * numberOfNights;
    }

    public void addAmenity(String amenityId) {
        if (amenityId == null || amenityId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Mã tiện nghi không được để trống."
            );
        }
        if (!amenityIds.contains(amenityId)) {
            amenityIds.add(amenityId);
        }
    }

    public boolean removeAmenity(String amenityId) {
        return amenityIds.remove(amenityId);
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAddressText() {
        return addressText;
    }

    public void setAddressText(String addressText) {
        this.addressText = addressText;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        if (Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Vĩ độ không hợp lệ.");
        }
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        if (Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Kinh độ không hợp lệ.");
        }
        this.longitude = longitude;
    }

    public int getMaxGuests() {
        return maxGuests;
    }

    public void setMaxGuests(int maxGuests) {
        if (maxGuests <= 0) {
            throw new IllegalArgumentException(
                    "Số khách tối đa phải lớn hơn 0."
            );
        }
        this.maxGuests = maxGuests;
    }

    public int getBedroomCount() {
        return bedroomCount;
    }

    public void setBedroomCount(int bedroomCount) {
        if (bedroomCount < 0) {
            throw new IllegalArgumentException("Số phòng ngủ không được âm.");
        }
        this.bedroomCount = bedroomCount;
    }

    public int getBathroomCount() {
        return bathroomCount;
    }

    public void setBathroomCount(int bathroomCount) {
        if (bathroomCount < 0) {
            throw new IllegalArgumentException("Số phòng tắm không được âm.");
        }
        this.bathroomCount = bathroomCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (!PENDING.equals(status) && !APPROVED.equals(status)
                && !REJECTED.equals(status) && !HIDDEN.equals(status)) {
            throw new IllegalArgumentException(
                    "Trạng thái bài đăng không hợp lệ."
            );
        }
        this.status = status;
    }

    public String getExchangeType() {
        return exchangeType;
    }

    public void setExchangeType(String exchangeType) {
        if (!DIRECT.equals(exchangeType) && !POINTS.equals(exchangeType)) {
            throw new IllegalArgumentException(
                    "Hình thức trao đổi không hợp lệ."
            );
        }
        this.exchangeType = exchangeType;
    }

    public int getPointsPerNight() {
        return pointsPerNight;
    }

    public void setPointsPerNight(int pointsPerNight) {
        if (pointsPerNight < 0) {
            throw new IllegalArgumentException("Điểm mỗi đêm không được âm.");
        }
        this.pointsPerNight = pointsPerNight;
    }

    public Date getAvailableFrom() {
        return availableFrom == null
                ? null : new Date(availableFrom.getTime());
    }

    public void setAvailableFrom(Date availableFrom) {
        if (availableFrom != null && availableTo != null
                && !availableTo.after(availableFrom)) {
            throw new IllegalArgumentException(
                    "Ngày bắt đầu phải trước ngày kết thúc."
            );
        }
        this.availableFrom = availableFrom == null
                ? null : new Date(availableFrom.getTime());
    }

    public Date getAvailableTo() {
        return availableTo == null
                ? null : new Date(availableTo.getTime());
    }

    public void setAvailableTo(Date availableTo) {
        if (availableTo != null && availableFrom != null
                && !availableTo.after(availableFrom)) {
            throw new IllegalArgumentException(
                    "Ngày kết thúc phải sau ngày bắt đầu."
            );
        }
        this.availableTo = availableTo == null
                ? null : new Date(availableTo.getTime());
    }

    public List<String> getAmenityIds() {
        return new ArrayList<>(amenityIds);
    }

    public void setAmenityIds(List<String> amenityIds) {
        List<String> newIds = new ArrayList<>();

        if (amenityIds != null) {
            for (String id : amenityIds) {
                if (id == null || id.trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Mã tiện nghi không được để trống."
                    );
                }
                if (!newIds.contains(id)) {
                    newIds.add(id);
                }
            }
        }

        this.amenityIds = newIds;
    }
}