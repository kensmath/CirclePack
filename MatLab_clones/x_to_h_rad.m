function h_rad = x_to_h_rad(x_rad)
%h_rad = x_to_h_rad(x_rad) Convert hyperbolic x_rad to h_rad

% x_rad < 0 means horocycle with -x_rad as eucl radius
h_rad=x_rad; 

if x_rad>0.0
	if x_rad>.0001
		h_rad=(-.5)*log(1.0-x_rad);
	else
		h_rad=(x_rad*(1.0+x_rad*(0.5+x_rad/3.0))/2);
	end
end

end

